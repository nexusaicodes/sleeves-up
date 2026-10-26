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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.platform.UpdateOffer
import com.checkin.app.platform.UpdatePrompt
import com.checkin.app.ui.components.LocalSnackbarHostState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Offers a newer Play build: Play's own flexible dialog once per version code, then a Restart
 * snackbar when the download it started has finished.
 *
 * Composed by the nav scaffold only, and asks Play once per [turn] it claims — once per activity
 * start that reaches the nav host — since a download can finish while the app is in the background
 * and Play reports it only when asked. A Restart snackbar the user lets go is offered again at the
 * next start.
 *
 * The restart waits while [sessionOpen]: it kills the process that holds the timer notification, and
 * a session is the wrong moment to hand the user an unasked-for relaunch.
 */
@Composable
internal fun AppUpdateOffer(turn: UpdateCheckTurn, sessionOpen: Boolean) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as CheckInApplication).container }
    val updates = container.appUpdates
    val settings = container.settings
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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

    val request = turn.requested
    LaunchedEffect(request) {
        if (!turn.claim(request)) return@LaunchedEffect
        val status = suspendCancellableCoroutine { cont -> updates.check { if (cont.isActive) cont.resume(it) } }
        // Play answers asynchronously; an answer arriving after a pause waits for the user to come
        // back rather than opening a dialog over whatever they moved on to.
        lifecycle.withResumed {
            when (val offer = UpdatePrompt.decide(status, settings.updateOfferedVersionCode())) {
                is UpdateOffer.Prompt -> if (updates.startFlexible(launcher)) {
                    settings.markUpdateOffered(offer.versionCode)
                }
                UpdateOffer.RestartReady -> downloaded = true
                UpdateOffer.None -> Unit
            }
        }
    }

    LaunchedEffect(downloaded, sessionOpen) {
        if (!downloaded || sessionOpen) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) updates.completeUpdate() else downloaded = false
    }
}
