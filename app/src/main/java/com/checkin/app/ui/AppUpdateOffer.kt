package com.checkin.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.platform.UpdateOffer
import com.checkin.app.platform.UpdatePrompt
import com.checkin.app.ui.components.LocalSnackbarHostState

/**
 * Offers a newer Play build: Play's own flexible dialog once per version code, then a Restart
 * snackbar when the download it started has finished.
 *
 * Composed by the nav scaffold only — never over the welcome tour, the naming step, either presence
 * gate or the launch-time notification ask, so it can never stack a second system dialog on one.
 * Checked on every resume, since a download can finish while the app is in the background and
 * Play reports it only when asked.
 *
 * The restart waits while [sessionOpen]: it kills the process that holds the timer notification, and
 * a session is the wrong moment to hand the user an unasked-for relaunch.
 */
@Composable
internal fun AppUpdateOffer(sessionOpen: Boolean) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as CheckInApplication).container }
    val updates = container.appUpdates
    val settings = container.settings
    val snackbarHostState = LocalSnackbarHostState.current
    val message = stringResource(R.string.update_downloaded)
    val action = stringResource(R.string.update_restart)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        // Unused: a refusal is answered by not asking again, and an acceptance reports itself
        // through the download watcher.
    }
    var downloaded by remember { mutableStateOf(false) }

    DisposableEffect(updates) {
        val stop = updates.watchDownloads { downloaded = true }
        onDispose { stop() }
    }

    LifecycleResumeEffect(updates) {
        // Play answers asynchronously; one arriving after a pause must not open a dialog over
        // whatever the user has moved on to.
        var resumed = true
        updates.check { status ->
            if (!resumed) return@check
            when (val offer = UpdatePrompt.decide(status, settings.updateOfferedVersionCode())) {
                is UpdateOffer.Prompt -> {
                    // Recorded before opening: the dialog pauses the activity, and the resume it
                    // returns to checks again.
                    settings.markUpdateOffered(offer.versionCode)
                    updates.startFlexible(launcher)
                }
                UpdateOffer.RestartReady -> downloaded = true
                UpdateOffer.None -> Unit
            }
        }
        onPauseOrDispose { resumed = false }
    }

    LaunchedEffect(downloaded, sessionOpen) {
        if (!downloaded || sessionOpen) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) updates.completeUpdate()
    }
}
