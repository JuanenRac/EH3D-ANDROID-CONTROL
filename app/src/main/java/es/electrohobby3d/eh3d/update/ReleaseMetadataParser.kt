// EH3D-ANDROID-CONTROL - Safe GitHub Release metadata parser.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.update

import es.electrohobby3d.eh3d.tr
import es.electrohobby3d.eh3d.trf
import org.json.JSONObject

/** A stable GitHub release and its explicitly named installable APK asset. */
data class AvailableUpdate(
    val version: SemanticVersion,
    val releaseName: String,
    val notes: String,
    val assetUrl: String,
)

/** Result of checking the trusted public release endpoint. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * Pure release-metadata gate used before any APK is downloaded. Keeping it
 * free of Context, HTTP and package-manager APIs makes the update channel's
 * trust decisions directly testable on the JVM.
 */
object ReleaseMetadataParser {
    const val REQUIRED_ASSET_NAME = "EH3D-ANDROID-CONTROL-release.apk"

    fun parseLatestStable(payload: String, installedVersionName: String): UpdateCheckResult {
        val release = JSONObject(payload)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            return UpdateCheckResult.UpToDate
        }
        val remoteVersion = SemanticVersion.parseStable(release.optStr("tag_name"))
            ?: return UpdateCheckResult.Failed(tr("La última versión publicada en GitHub no tiene una etiqueta estable MAYOR.MENOR.PARCHE."))
        val localVersion = SemanticVersion.parseStable(installedVersionName)
            ?: return UpdateCheckResult.Failed(tr("La versión instalada no es un número de versión estable."))
        if (remoteVersion <= localVersion) return UpdateCheckResult.UpToDate

        val assets = release.optJSONArray("assets")
            ?: return UpdateCheckResult.Failed(trf("La versión v%s no tiene el archivo %s.", remoteVersion, REQUIRED_ASSET_NAME))
        val asset = (0 until assets.length())
            .asSequence()
            .map { assets.optJSONObject(it) }
            .firstOrNull { it?.optStr("name") == REQUIRED_ASSET_NAME }
            ?: return UpdateCheckResult.Failed(trf("La versión v%s no tiene el archivo %s.", remoteVersion, REQUIRED_ASSET_NAME))
        val assetUrl = asset.optStr("browser_download_url")
        if (!assetUrl.startsWith("https://")) {
            return UpdateCheckResult.Failed(tr("El enlace del APK de la versión no usa HTTPS."))
        }
        return UpdateCheckResult.Available(
            AvailableUpdate(
                version = remoteVersion,
                releaseName = release.optStr("name", "EH3D Control v${remoteVersion}"),
                notes = release.optStr("body").trim(),
                assetUrl = assetUrl,
            ),
        )
    }
}

/** A string member, or [default] when it is missing or not a string. */
internal fun JSONObject.optStr(name: String, default: String = ""): String = if (has(name) && !isNull(name)) optString(name, default) else default
