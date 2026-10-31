// EH3D-ANDROID-CONTROL - Lifecycle-aware application update state.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d.update

import es.electrohobby3d.eh3d.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The update UI never reports an update as installed: Android owns that final step. */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val update: AvailableUpdate) : AppUpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Int?) : AppUpdateState
    data class InstallPermissionRequired(val update: AvailableUpdate) : AppUpdateState
    data object Installing : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = GitHubReleaseUpdater(application.applicationContext)
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val downloading = java.util.concurrent.atomic.AtomicBoolean(false)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    /** Safe to call at startup: it only reads release metadata, never downloads an APK. */
    fun checkForUpdate() {
        if (mutableState.value is AppUpdateState.Checking) return
        viewModelScope.launch {
            mutableState.value = AppUpdateState.Checking
            mutableState.value = when (val result = updater.checkForUpdate()) {
                UpdateCheckResult.UpToDate -> AppUpdateState.UpToDate
                is UpdateCheckResult.Available -> AppUpdateState.Available(result.update)
                is UpdateCheckResult.Failed -> AppUpdateState.Failed(result.message)
            }
        }
    }

    /** Called only after the operator explicitly chooses "Descargar e instalar". */
    fun downloadAndInstall(update: AvailableUpdate) {
        // A second tap while a download runs would start another one over the same file: the first claim is made at once, before the first byte arrives.
        if (!downloading.compareAndSet(false, true)) return
        viewModelScope.launch {
            val result = try {
                updater.download(update) { progress -> mutableState.value = AppUpdateState.Downloading(update, progress) }
            } finally { downloading.set(false) }
            when (result) {
                is UpdateDownloadResult.Failed -> mutableState.value = AppUpdateState.Failed(result.message)
                is UpdateDownloadResult.ReadyToInstall -> {
                    if (updater.canRequestPackageInstalls()) {
                        mutableState.value = AppUpdateState.Installing
                        updater.launchSystemInstaller(result.apk)
                    } else {
                        mutableState.value = AppUpdateState.InstallPermissionRequired(update)
                    }
                }
            }
        }
    }

    fun openInstallPermissionSettings() = updater.openInstallPermissionSettings()

    /**
     * Called when EH3D Control returns to the foreground after its
     * per-app unknown-sources permission screen. The APK was verified before
     * that screen was opened and is verified again here before launch.
     */
    fun resumeInstallAfterPermissionApproval() {
        val permissionState = mutableState.value as? AppUpdateState.InstallPermissionRequired ?: return
        if (!updater.canRequestPackageInstalls()) return
        val apk = updater.cachedInstallableApk()
        if (apk == null) {
            mutableState.value = AppUpdateState.Failed(
                tr("La actualización descargada ya no está disponible o no es válida. Vuelve a comprobar si hay actualizaciones."),
            )
            return
        }
        mutableState.value = AppUpdateState.Installing
        updater.launchSystemInstaller(apk)
    }

    /** Hides a startup prompt; Settings can always issue a new explicit check. */
    fun dismiss() {
        mutableState.value = AppUpdateState.Idle
    }
}
