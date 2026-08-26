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

/** Camera drives the presence check; notifications carry the running timer and its reminder. */
private val PRESENCE_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.POST_NOTIFICATIONS,
)

/**
 * The one entry point to a presence check, and the only place either permission is asked for.
 *
 * Everything the check needs is raised here, in the order Play policy requires and at the moment the
 * feature actually demands it: the prominent disclosure, then the runtime permissions, then the
 * capture itself. Nothing is asked at launch, so the app is fully browsable before a first check-in.
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
    var notificationsGranted by rememberSaveable { mutableStateOf(context.hasNotificationPermission()) }
    // Separates "not asked yet" from "asked and refused" — only the latter earns the recovery screen.
    var cameraRefused by rememberSaveable { mutableStateOf(false) }
    // Saveable, or a rotation on the recovery screen re-fires the system dialog the user just
    // answered — and rotating while it is still up launches a second request against the pending one.
    var requested by rememberSaveable { mutableStateOf(false) }
    // Whether a system permission dialog is on screen right now — which `requested` cannot answer,
    // since it stays true after a refusal and keying on it would shut the camera out permanently.
    // It outranks the camera in the `when` below: granting CAMERA while refusing POST_NOTIFICATIONS
    // is one tap apart in the same dialog, so the next visit re-asks with the camera already
    // granted, and that dialog is a translucent activity that never stops this one — the preview
    // underneath would keep streaming and pass the check while the user was still reading a system
    // prompt they never connected to checking in. Saveable because the dialog outlives a rotation
    // beneath it; the launcher's callback always fires, so this cannot latch on.
    var requestInFlight by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Only the camera decides whether the check can run. A refused POST_NOTIFICATIONS costs the
        // timer notification, the session reminder and every nudge, all of which Notifier guards.
        requestInFlight = false
        cameraGranted = context.hasCameraPermission()
        notificationsGranted = context.hasNotificationPermission()
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
            permissionLauncher.launch(PRESENCE_PERMISSIONS)
        }
    }

    // The recovery screen sends the user to system settings; this is what notices they came back.
    LifecycleResumeEffect(Unit) {
        if (!cameraGranted && context.hasCameraPermission()) {
            cameraGranted = true
            cameraRefused = false
        }
        notificationsGranted = context.hasNotificationPermission()
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
                    permissionLauncher.launch(PRESENCE_PERMISSIONS)
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

        // The system dialog is up: hold the full-screen surface rather than flashing a screen behind it.
        else -> Box(modifier = Modifier.fillMaxSize())
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.hasNotificationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Unwraps the theming/base-context wrappers Compose may hand out to reach the hosting Activity. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
