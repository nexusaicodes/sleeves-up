package com.checkin.app.ui.presence

import android.hardware.camera2.CaptureResult
import android.os.SystemClock
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Tertiary
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.icons.Cross
import com.adevinta.spark.icons.LeboncoinIcons
import com.adevinta.spark.icons.LockOutline
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.R
import com.checkin.app.ui.components.AppIconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "PresenceCheckScreen"

/** How long the "you're here" confirmation stays up before the gate hands control back. */
private const val SUCCESS_CONFIRMATION_MS = 800L

/**
 * The check stage of [PresenceGate]: the front camera confirms someone is there, reached once the
 * disclosure and the permission request are behind it.
 *
 * **No image is captured, written or read.** Face detection is done by the camera hardware and
 * arrives as capture-result metadata on the preview stream, so the frames stay in the camera
 * pipeline and the app only ever sees a count. There is no file to delete, nothing to strand if the
 * gate is dismissed mid-check, and nothing to sweep at startup.
 *
 * **There is nothing to press.** The check completes itself the moment the camera reports a face;
 * a confirm button would only ask the user to restate the hardware's answer, at the cost of a reach
 * to the bottom of the screen just when their face is least likely to still be in frame.
 * [onAuthSuccess] fires after a brief confirmation, and that delay is composition-scoped, so a gate
 * dismissed within it never succeeds.
 *
 * Device unlock is offered immediately when the camera cannot run a check at all — no face-detection
 * mode, a mode the HAL then declines to apply, an unavailable provider, or a bind that threw — and
 * otherwise once the camera has looked for [DEVICE_UNLOCK_OFFERED_AFTER_MS] without finding anyone
 * (see that constant for why time is what unlocks it). [onAuthSuccess] fires once either path passes.
 *
 * Device unlock needs a screen lock to exist, and on a device with none the fallback is a route to
 * the settings screen that creates one rather than a button that isn't there: every check-in and
 * check-out comes through here, so this screen must never be left with no control but Dismiss.
 */
// Binding the camera fails as whatever the vendor implementation throws — an unavailable front
// camera, a lifecycle race, a device-specific fault. There is one recovery for all of them: log it
// and offer device unlock.
@Suppress("TooGenericExceptionCaught")
@Composable
fun PresenceCheckScreen(onAuthSuccess: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    // All saved, because a rotation must not cost the user the escape hatch, the guidance that sent
    // them to it, or the time they have already spent waiting on it. The instant is saved rather
    // than the remaining delay: a config change or a trip to the notification shade restarts the
    // effect, so a bare countdown would begin again from zero every time, and a phone being turned
    // over in the hand while the camera fails to find a face would reach the fallback late or never.
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var searchTimedOut by rememberSaveable { mutableStateOf(false) }
    val searchStartedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    var successMessage by remember { mutableStateOf<String?>(null) }
    // Set for as long as the device-unlock prompt is up. The camera keeps streaming behind it, so
    // without this a face found while the user is answering the prompt would pass the check under
    // the dialog — leaving a live prompt over a screen that has already moved on, and a second
    // onAuthSuccess behind it if they then complete it. Saved, because the prompt outlives the
    // composition that raised it: rotating with it on screen recreates this screen underneath, and a
    // flag reset to false there passes the check under a dialog the user is still answering. Safe
    // only because the prompt below is rebuilt with each activity — see [rememberDeviceUnlock].
    var usingDeviceUnlock by rememberSaveable { mutableStateOf(false) }
    // Fed by the capture callback off the main thread; the rules are in CameraReadings.
    val readings = remember { CameraReadings() }
    val cameraUsable by readings.cameraUsable.collectAsState()
    val facePresent by readings.facePresent.collectAsState()
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Tracks disposal so the async provider callback can release the camera if the gate is gone
    // before the provider resolves (onDispose would otherwise see a null provider and skip unbind).
    val gateDisposed = remember { AtomicBoolean(false) }

    // Re-read on every resume, not once per composition: the recovery this screen points a user
    // with no screen lock at is a round trip through system settings, which returns with no result
    // and does not recreate the activity, so a value cached at first composition would still say
    // "no device unlock" after they had just set one up.
    var unlockStatus by remember { mutableIntStateOf(deviceUnlockStatus(context)) }
    LifecycleResumeEffect(Unit) {
        unlockStatus = deviceUnlockStatus(context)
        onPauseOrDispose { }
    }
    // Anything short of BIOMETRIC_SUCCESS — no screen lock, a sensor the platform won't vouch for,
    // an unexpected status — is offered the settings route instead, because the recovery for all of
    // them is the same screen and the alternative is no control at all. It needs no activity, unlike
    // BiometricPrompt, so there is no status left over for which this screen offers nothing.
    val canUseBiometric = activity != null && unlockStatus == BiometricManager.BIOMETRIC_SUCCESS

    // Read in composition rather than through the context inside each callback, so a locale or
    // configuration change re-resolves them with the rest of the screen.
    val unavailableMessage = stringResource(R.string.biometric_unavailable)
    val promptTitle = stringResource(R.string.biometric_title)
    val promptSubtitle = stringResource(R.string.biometric_subtitle)
    val faceDetectedMessage = stringResource(R.string.presence_face_detected)

    // Both success paths run through this rather than the parameter. The camera's effect is keyed on
    // Unit and the prompt's callback is registered with the activity, so each would otherwise capture
    // the caller's lambda exactly once and hold it for the life of the screen — and that lambda
    // carries the check-in itself, so a stale one writes against state the caller has since replaced.
    val currentOnAuthSuccess by rememberUpdatedState(onAuthSuccess)

    // Rebuilt whenever the activity is, so the prompt's callback is always live.
    val launchBiometric = rememberDeviceUnlock(
        activity = activity,
        promptTitle = promptTitle,
        promptSubtitle = promptSubtitle,
        unavailableMessage = unavailableMessage,
        onSucceeded = { currentOnAuthSuccess() },
        onRaised = { usingDeviceUnlock = true },
        onEnded = { usingDeviceUnlock = false },
        onError = { errorMessage = it },
    )

    // The check itself. Keyed on Unit and latched by `first`, so the face leaving frame during the
    // confirmation cannot cancel a check that has already passed — which keying on the live value
    // would do, since a LaunchedEffect's coroutine dies with its key. It stays composition-scoped
    // on purpose: a gate dismissed inside the confirmation must not go on to succeed.
    LaunchedEffect(Unit) {
        snapshotFlow { PresenceCheckRules.passes(facePresent, usingDeviceUnlock) }.first { it }
        // Whatever the camera or an abandoned prompt said before this frame is now answered.
        errorMessage = null
        successMessage = faceDetectedMessage
        delay(SUCCESS_CONFIRMATION_MS)
        currentOnAuthSuccess()
    }

    // The wait that guarantees this screen always ends in something the user can act on. It runs off
    // a saved instant rather than a fresh countdown, so what is left of the budget survives every
    // rotation and every trip out of the app, and it is measured from the gate opening rather than
    // from the camera streaming — a camera that binds and then never delivers a result would start
    // no clock at all, leaving Dismiss as the only control.
    LaunchedEffect(Unit) {
        if (searchTimedOut) return@LaunchedEffect
        delay(PresenceCheckRules.remainingWaitMs(searchStartedAt, SystemClock.elapsedRealtime()))
        searchTimedOut = true
    }

    // A camera written off mid-wait offers the fallback at once, and latching it here is what stops
    // the offer being retracted: a HAL that reports OFF for its first results and then starts
    // detecting would otherwise take the button back out from under a user already reaching for it.
    // The explanatory message is free to go — it says the camera cannot look, which stopped being
    // true — but the escape hatch, once offered, stays.
    LaunchedEffect(cameraUsable) {
        if (cameraUsable == false) searchTimedOut = true
    }

    DisposableEffect(lifecycleOwner) {
        // See CameraReadings.onStop: a face from before the stop must not pass the check after it.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) readings.onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gateDisposed.set(true)
            cameraProvider?.unbindAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SparkTheme.colors.background),
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val provider = try {
                            cameraProviderFuture.get()
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera provider unavailable", e)
                            readings.onUnusable()
                            return@addListener
                        }
                        if (gateDisposed.get()) {
                            // Gate left composition before the provider resolved — release the camera
                            // now instead of binding one that nothing remains to unbind.
                            provider.unbindAll()
                            return@addListener
                        }
                        cameraProvider = provider

                        // Asked before binding, because the mode has to be set on the request the
                        // session is created with.
                        val mode = frontCameraDetectMode(provider)
                        if (mode == null) {
                            readings.onUnusable()
                            return@addListener
                        }

                        val preview = faceDetectingPreview(mode) { result ->
                            readings.onResult(
                                appliedMode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE),
                                facesReported = result.get(CaptureResult.STATISTICS_FACES).orEmpty().size,
                            )
                        }.also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                            )
                            readings.onBound()
                        } catch (e: Exception) {
                            // A camera that failed to bind will never report a face, so the fallback
                            // is offered now rather than after the wait.
                            Log.e(TAG, "Camera bind failed", e)
                            readings.onUnusable()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AppIconButton(
            icon = LeboncoinIcons.Cross,
            contentDescription = stringResource(R.string.presence_dismiss),
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            successMessage?.let { msg ->
                Text(
                    text = msg,
                    color = SparkTheme.colors.main,
                    style = SparkTheme.typography.body2,
                    // Announce the outcome to TalkBack as it changes.
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = SparkTheme.colors.error,
                    style = SparkTheme.typography.body2,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (cameraUsable == false) {
                Text(
                    text = stringResource(
                        if (canUseBiometric) {
                            R.string.presence_unsupported
                        } else {
                            R.string.presence_unsupported_no_unlock
                        },
                    ),
                    color = SparkTheme.colors.onBackgroundVariant,
                    style = SparkTheme.typography.body2,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else if (successMessage == null) {
                // What the screen says while it looks. Shown from the moment the gate opens rather
                // than from the first capture result, because a camera still being opened is a camera
                // that has not found anyone either — and the alternative is a bare preview that
                // states nothing at all for as long as the bind takes. It changes with the wait
                // running out, so the button that appears then is explained rather than simply there.
                Text(
                    text = stringResource(
                        if (searchTimedOut) R.string.presence_looking_slow else R.string.presence_looking,
                    ),
                    color = SparkTheme.colors.onBackgroundVariant,
                    style = SparkTheme.typography.body2,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            val offerBiometric = PresenceCheckRules.offersFallback(
                passed = successMessage != null,
                usingDeviceUnlock = usingDeviceUnlock,
                searchTimedOut = searchTimedOut,
            )
            if (offerBiometric) {
                if (canUseBiometric) {
                    // Spark draws a button's icon with no description: the label conveys the action.
                    Button.Tertiary(
                        onClick = { launchBiometric() },
                        text = stringResource(R.string.biometric_use_device_unlock),
                        icon = SparkIcon.Vector(Icons.Default.Fingerprint),
                    )
                } else {
                    // The fallback the message names does not exist yet. Handing the user the screen
                    // that creates it keeps the check gated without leaving only Dismiss — and since
                    // check-out is as gated as check-in, a dead end here would leave the session open
                    // until the day boundary wrote a full day onto a row nothing can edit.
                    Button.Tertiary(
                        onClick = {
                            if (!openScreenLockSettings(context)) {
                                errorMessage = unavailableMessage
                            }
                        },
                        text = stringResource(R.string.presence_set_up_screen_lock),
                        icon = LeboncoinIcons.LockOutline,
                    )
                }
            }
        }
    }
}
