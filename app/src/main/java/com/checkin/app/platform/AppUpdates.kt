package com.checkin.app.platform

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Seam over Play In-App Updates, flexible flow only.
 *
 * The library asks the Play Store app over IPC and declares no permission; the Play Store does the
 * network lookup, so the app's no-`INTERNET` claim stands. Every call answers "nothing to do" rather
 * than throwing: a sideloaded or debug install, an account that doesn't own the app, a missing or
 * outdated Play Store and a failed `Task` all look the same to a user, which is no offer at all.
 */
interface AppUpdates {
    /** Reports the current [UpdateStatus], or null when Play cannot answer. Called on the main thread. */
    fun check(onResult: (UpdateStatus?) -> Unit)

    /** Opens Play's flexible dialog for the build the last [check] reported; false if it could not. */
    fun startFlexible(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean

    /** Installs a finished flexible download, restarting the app. */
    fun completeUpdate()

    /** Calls [onDownloaded] when a flexible download finishes; returns the unregister call. */
    fun watchDownloads(onDownloaded: () -> Unit): () -> Unit
}

class PlayAppUpdates(context: Context) : AppUpdates {
    private val manager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(context.applicationContext) }

    // An AppUpdateInfo starts at most one flow, so the dialog is opened from the one just checked.
    private var lastInfo: AppUpdateInfo? = null

    override fun check(onResult: (UpdateStatus?) -> Unit) {
        runCatching {
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    lastInfo = info
                    onResult(info.toStatus())
                }
                .addOnFailureListener { onResult(null) }
        }.onFailure { onResult(null) }
    }

    override fun startFlexible(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        val info = lastInfo ?: return false
        lastInfo = null
        return runCatching {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
        }.getOrDefault(false)
    }

    override fun completeUpdate() {
        runCatching { manager.completeUpdate() }
    }

    override fun watchDownloads(onDownloaded: () -> Unit): () -> Unit {
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) onDownloaded()
        }
        runCatching { manager.registerListener(listener) }
        return { runCatching { manager.unregisterListener(listener) } }
    }

    private fun AppUpdateInfo.toStatus() = UpdateStatus(
        availableVersionCode = availableVersionCode()
            .takeIf { updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE },
        flexibleAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
        downloaded = installStatus() == InstallStatus.DOWNLOADED,
    )
}
