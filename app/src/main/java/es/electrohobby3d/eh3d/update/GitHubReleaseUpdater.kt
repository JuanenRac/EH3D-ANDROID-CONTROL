// EH3D-ANDROID-CONTROL - Safe GitHub Release update client.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
//
// Uses java.net.HttpURLConnection, the same stdlib-only HTTP the rest of this
// app already uses for EH3D-SERVER (see network/Eh3dApi.kt) - adding
// a whole new networking dependency (OkHttp, Retrofit, ...) just for the one
// update check this class does would be a second HTTP stack in an app that
// otherwise deliberately has none.
package es.electrohobby3d.eh3d.update

import es.electrohobby3d.eh3d.tr
import es.electrohobby3d.eh3d.trf
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import es.electrohobby3d.eh3d.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Result of downloading the APK before control is handed to Android's installer. */
sealed interface UpdateDownloadResult {
    data class ReadyToInstall(val apk: File) : UpdateDownloadResult
    data class Failed(val message: String) : UpdateDownloadResult
}

/**
 * Queries the official release feed and downloads only the named stable asset.
 * Signature enforcement happens again in Android's package installer, which
 * refuses an update not signed by the same certificate as the installed app.
 */
class GitHubReleaseUpdater(private val context: Context) {

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EH3D-ANDROID-CONTROL/${BuildConfig.VERSION_NAME}")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@withContext UpdateCheckResult.Failed(
                        describeHttpFailure(
                            code = connection.responseCode,
                            rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining"),
                            rateLimitReset = connection.getHeaderField("X-RateLimit-Reset"),
                        ),
                    )
                }
                val payload = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                ReleaseMetadataParser.parseLatestStable(payload, BuildConfig.VERSION_NAME)
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            UpdateCheckResult.Failed(trf("No se pudo consultar los lanzamientos de GitHub: %s", error.message ?: "error de red"))
        } catch (error: Exception) {
            UpdateCheckResult.Failed(trf("Los datos del lanzamiento no son válidos: %s", error.message ?: "error desconocido"))
        }
    }

    suspend fun download(update: AvailableUpdate, onProgress: (Int?) -> Unit): UpdateDownloadResult =
        withContext(Dispatchers.IO) {
            try {
                // GitHub's own `browser_download_url` for a release asset is a
                // stable github.com link that 302-redirects to a short-lived,
                // signed objects.githubusercontent.com URL - a different host,
                // which HttpURLConnection's own `instanceFollowRedirects` does
                // not reliably follow while keeping this request's headers.
                // Followed by hand here so a header this app actually needs is
                // never silently dropped partway.
                val connection = openFollowingRedirects(update.assetUrl)
                try {
                    if (connection.responseCode !in 200..299) {
                        return@withContext UpdateDownloadResult.Failed(trf("La descarga del APK devolvió el código HTTP %s.", connection.responseCode))
                    }
                    val expectedLength = connection.contentLengthLong
                    val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
                    val partial = File(updateDirectory, "${APK_FILE_NAME}.part")
                    val apk = File(updateDirectory, APK_FILE_NAME)
                    partial.delete()
                    apk.delete()
                    var downloaded = 0L
                    connection.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                onProgress(
                                    if (expectedLength > 0) ((downloaded * 100) / expectedLength).toInt() else null,
                                )
                            }
                            output.flush()
                        }
                    }
                    if (expectedLength >= 0 && downloaded != expectedLength) {
                        partial.delete()
                        return@withContext UpdateDownloadResult.Failed(
                            trf("La descarga del APK quedó incompleta: se esperaban %s bytes y llegaron %s.", expectedLength, downloaded),
                        )
                    }
                    if (!partial.renameTo(apk)) {
                        return@withContext UpdateDownloadResult.Failed(tr("No se pudo finalizar el archivo APK descargado."))
                    }
                    val packageInfo = packageArchiveInfo(apk)
                        ?: run {
                            apk.delete()
                            return@withContext UpdateDownloadResult.Failed(tr("El archivo descargado no es un paquete de Android válido."))
                        }
                    if (packageInfo.packageName != context.packageName) {
                        apk.delete()
                        return@withContext UpdateDownloadResult.Failed(tr("El APK descargado tiene un nombre de paquete inesperado."))
                    }
                    if (packageVersionCode(packageInfo) <= installedVersionCode()) {
                        apk.delete()
                        return@withContext UpdateDownloadResult.Failed(tr("El APK descargado no es más nuevo que la aplicación instalada."))
                    }
                    if (signedByAnotherKey(packageInfo)) {
                        apk.delete()
                        return@withContext UpdateDownloadResult.Failed(tr("El APK descargado no está firmado con la misma clave que la aplicación instalada: Android no lo instalaría."))
                    }
                    UpdateDownloadResult.ReadyToInstall(apk)
                } finally {
                    connection.disconnect()
                }
            } catch (error: IOException) {
                UpdateDownloadResult.Failed(trf("No se pudo descargar el APK: %s", error.message ?: "error de red"))
            } catch (error: Exception) {
                UpdateDownloadResult.Failed(trf("No se pudo validar el APK: %s", error.message ?: "error desconocido"))
            }
        }

    /** Follows a bounded chain of redirects by hand, re-sending the same
     * User-Agent on every hop - `instanceFollowRedirects` alone does not
     * guarantee that across a host change (github.com to
     * objects.githubusercontent.com), and a request GitHub's asset host
     * rejects for a missing header would otherwise fail with a confusing
     * "empty body" rather than a real HTTP status. */
    private fun openFollowingRedirects(url: String): HttpURLConnection {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "EH3D-ANDROID-CONTROL/${BuildConfig.VERSION_NAME}")
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 30_000
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) return connection
                // A relative Location is resolved against the address that answered, and the chain never leaves HTTPS (the APK is verified anyway, but nothing should travel in the clear).
                currentUrl = URL(URL(currentUrl), location).toString()
                if (!currentUrl.startsWith("https://")) throw IOException(tr("La descarga del APK se desvió a una dirección que no es HTTPS."))
                return@repeat
            }
            return connection
        }
        throw IOException(tr("La descarga del APK tuvo demasiadas redirecciones."))
    }

    /** Android 8+ requires a one-time per-app approval before opening an APK installer. */
    fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens only Android's own per-app unknown-source approval page. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Delegates the final signature and user-consent checks to Android's package installer. */
    fun launchSystemInstaller(apk: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updateprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * Returns the already downloaded update only when it is still a valid,
     * newer APK for this exact application. This makes returning from
     * Android's unknown-sources approval screen resume the install without a
     * second network download or trusting a stale cache file.
     */
    fun cachedInstallableApk(): File? {
        val apk = File(File(context.cacheDir, UPDATE_DIRECTORY), APK_FILE_NAME)
        if (!apk.isFile) return null
        val packageInfo = packageArchiveInfo(apk) ?: return null
        if (packageInfo.packageName != context.packageName) return null
        if (signedByAnotherKey(packageInfo)) return null
        return apk.takeIf { packageVersionCode(packageInfo) > installedVersionCode() }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.PackageInfoFlags.of(signatureFlags().toLong()),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags())
        }

    /** What to ask the package manager for so that the certificates come with the package. */
    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else android.content.pm.PackageManager.GET_SIGNATURES

    /** The SHA-256 of every certificate in the signing history of a package; empty when the package manager did not give them. */
    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else info.signatures
        return signatures.orEmpty().map { SignerCheck.digest(it.toByteArray()) }.toSet()
    }

    /**
     * True when the downloaded package and the installed app are both known to be signed and share no certificate: Android would refuse the update, so it is not offered.
     * When either side cannot be read the decision is left to Android's installer, which makes the same check.
     */
    private fun signedByAnotherKey(candidate: PackageInfo): Boolean =
        runCatching { SignerCheck.mismatch(signerDigests(installedPackageInfo()), signerDigests(candidate)) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(signatureFlags().toLong()),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, signatureFlags())
        }

    private fun installedVersionCode(): Long = packageVersionCode(installedPackageInfo())

    private companion object {
        const val REPOSITORY = "JuanenRac/EH3D-ANDROID-CONTROL"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/${REPOSITORY}/releases/latest"
        const val UPDATE_DIRECTORY = "updates"
        const val APK_FILE_NAME = "EH3D-ANDROID-CONTROL-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DEFAULT_BUFFER_SIZE = 8192
        const val MAX_REDIRECTS = 5
    }
}

/** GitHub's unauthenticated REST API answers a 403 (not 429) once the calling
 * IP's own 60-requests-an-hour ceiling is spent - `X-RateLimit-Remaining: 0`
 * on that same response is how it says so, real and documented, not a
 * guess. Every device on the same home network shares that ceiling (it is
 * keyed by public IP, not by app or device), so a developer machine making
 * many API calls can spend it for a phone on the same Wi-Fi too. Found for
 * real: a plain "GitHub devolvió el código HTTP 403" told the operator
 * nothing about why, or that it needed no fix at all, just a short wait.
 *
 * A top-level, pure function (no HttpURLConnection, no Context) so it is
 * directly unit-testable - see GitHubReleaseUpdaterHttpFailureTest.kt. */
internal fun describeHttpFailure(
    code: Int,
    rateLimitRemaining: String?,
    rateLimitReset: String?,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
): String {
    if (code == HttpURLConnection.HTTP_FORBIDDEN && rateLimitRemaining == "0") {
        val minutes = rateLimitReset?.toLongOrNull()?.let { reset ->
            val secondsLeft = reset - nowEpochSeconds
            if (secondsLeft > 0) (secondsLeft / 60L) + 1 else null
        }
        val whenText = if (minutes != null) trf("en unos %s minuto(s)", minutes) else tr("dentro de un rato")
        return tr("GitHub ha limitado temporalmente las consultas sin iniciar sesión desde esta red ") +
            tr("(comparte el límite con cualquier otro dispositivo de la misma casa/Wi-Fi). ") +
            trf("Inténtalo de nuevo %s; no hace falta hacer nada más.", whenText)
    }
    return trf("GitHub devolvió el código HTTP %s.", code)
}
