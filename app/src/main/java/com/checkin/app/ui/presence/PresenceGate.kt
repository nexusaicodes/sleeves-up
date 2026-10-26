package com.checkin.app.ui.presence

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.notify.holdsNotificationPermission
import com.checkin.app.notify.notificationsNeedRuntimeGrant

/**
 * Camera drives the presence check; notifications carry the running timer and its reminder, and are
 * asked for only where they are a runtime permission (API 33 up).
 */
private fun presencePermissions(): Array<String> = if (notificationsNeedRuntimeGrant()) {
    arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
} else {
    arrayOf(Manifest.permission.CAMERA)
}

/**
 * The one entry point to a presence check, and the only place the camera permission is asked for
 * (`POST_NOTIFICATIONS` is also asked once at first open, by `AppRoot`).
 *
 * Everything the check needs is raised here, in the order Play policy requires and at the moment the
 * feature actually demands it: the prominent disclosure, then the runtime permissions, then the
 * check itself. Nothing is asked at launch, so the app is fully browsable before a first check-in.
 * [onDismiss] backs out at any stage, leaving the caller's action unperformed.
 *
 * Two callers, and both matter: `AppRoot` raises it above the whole app for a check requested from
 * a notification, and `AppNavScaffold` raises it over the nav host for one requested from the
 * Check-In tab. Same gate, different z-rank — a change here lands on both.
 */
@Composable
fun PresenceGate(onAuthSuccess: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) {
        (context.applicationContext as CheckInApplication).container.settings
    }

    var disclosureSeen by rememberSaveable { mutableStateOf(settings.hasSeenCameraDisclosure()) }
    var cameraGranted by rememberSaveable { mutableStateOf(context.hasCameraPermission()) }
    var notificationsGranted by rememberSaveable { mutableStateOf(context.holdsNotificationPermission()) }
    // Separates "not asked yet" from "asked and refused" — only the latter earns the recovery screen.
    var cameraRefused by rememberSaveable { mutableStateOf(false) }
    // Saveable, or a rotation on the recovery screen re-fires the system dialog the user just
    // answered — and rotating while it is still up launches a second request against the pending one.
    var requested by rememberSaveable { mutableStateOf(false) }
    // Whether a system permission dialog is on screen right now — which `requested` cannot answer,
    // since it stays true after a refusal and keying on it would shut the camera out permanently.
    // It outranks the camera in the `when` below: a visit can re-ask for POST_NOTIFICATIONS with
    // the camera already granted, and that dialog is a translucent activity that never stops this
    // one — the preview underneath would keep streaming and pass the check while the user was still
    // reading a system prompt. Saveable because the dialog outlives a rotation beneath it; the
    // launcher's callback always fires, so this cannot latch on.
    var requestInFlight by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Only the camera decides whether the check can run. A refused POST_NOTIFICATIONS costs the
        // timer notification, the session reminder and every nudge, all of which Notifier guards.
        requestInFlight = false
        cameraGranted = context.hasCameraPermission()
        notificationsGranted = context.holdsNotificationPermission()
        cameraRefused = !cameraGranted
    }

    // Asking is a side effect of reaching the "disclosed but ungranted" state, so accepting the
    // disclosure and arriving with a revoked permission both land on the same system dialog.
    // Notifications are tested separately from the camera rather than folded into it: granting the
    // camera and refusing notifications is one tap apart in the same combined dialog, and keying
    // only on the camera would make that the last time the app ever asked. `requested` holds it to
    // one request per visit to the gate.
    val allGranted = cameraGranted && notificationsGranted
    LaunchedEffect(disclosureSeen, allGranted) {
        if (disclosureSeen && !allGranted && !requested) {
            requested = true
            requestInFlight = true
            permissionLauncher.launch(presencePermissions())
        }
    }

    // The recovery screen sends the user to system settings; this is what notices they came back.
    LifecycleResumeEffect(Unit) {
        if (!cameraGranted && context.hasCameraPermission()) {
            cameraGranted = true
            cameraRefused = false
        }
        notificationsGranted = context.holdsNotificationPermission()
        onPauseOrDispose {}
    }

    when {
        !disclosureSeen -> CameraDisclosureScreen(
            onAccept = {
                settings.markCameraDisclosureSeen()
                disclosureSeen = true
            },
            onDismiss = onDismiss,
        )

        requestInFlight -> Box(modifier = Modifier.fillMaxSize())

        cameraGranted -> PresenceCheckScreen(onAuthSuccess = onAuthSuccess, onDismiss = onDismiss)

        cameraRefused -> GateMessageScreen(
            icon = Icons.Rounded.PhotoCamera,
            title = stringResource(R.string.camera_permission_title),
            message = stringResource(R.string.camera_permission_message),
            actionLabel = stringResource(R.string.camera_permission_button),
            onAction = {
                val activity = context.findActivity()
                // Android stops showing the dialog after a second refusal, at which point the only
                // route left is system settings.
                if (activity != null && activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    requestInFlight = true
                    permissionLauncher.launch(presencePermissions())
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }
            },
            onDismiss = onDismiss,
        )

        // Disclosed but not yet asked: the effect above is about to raise the dialog, so hold the
        // full-screen surface rather than flashing a screen behind it.
        else -> Box(modifier = Modifier.fillMaxSize())
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Unwraps the theming/base-context wrappers Compose may hand out to reach the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
