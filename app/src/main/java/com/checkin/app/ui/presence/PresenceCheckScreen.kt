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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.checkin.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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
 * **There is nothing to press.** The check completes itself the moment the camera reports a face:
 * the confirm button this screen used to carry only ever asked the user to restate an answer the
 * hardware had already given, and asking for it cost a reach to the bottom of the screen at exactly
 * the moment their face was least likely to still be in frame. [onAuthSuccess] fires after a brief
 * confirmation, and that delay is composition-scoped, so a gate dismissed within it never succeeds.
 *
 * Device unlock is offered immediately when the camera cannot run a check at all — no face-detection
 * mode, a mode the HAL then declines to apply, an unavailable provider, or a bind that threw — and
 * otherwise once the camera has looked for [DEVICE_UNLOCK_OFFERED_AFTER_MS] without finding
 * anyone. With no tap there is no attempt to count, so time is what unlocks the fallback; without it
 * a camera that detects perfectly well but never resolves this particular user would leave the
 * screen with no exit but Dismiss. [onAuthSuccess] fires once either path passes.
 *
 * Device unlock needs a screen lock to exist, and on a device with none the fallback is a route to
 * the settings screen that creates one rather than a button that isn't there: an unusable camera and
 * an absent credential would otherwise leave this screen with no control but Dismiss, and every
 * check-in and check-out in the app comes through here.
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
    // That is the one way out of a gate every check-in and check-out routes through.
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var searchTimedOut by rememberSaveable { mutableStateOf(false) }
    val searchStartedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    var successMessage by remember { mutableStateOf<String?>(null) }
    // Set for as long as the device-unlock prompt is up. The camera keeps streaming behind it, so
    // without this a face found while the user is answering the prompt would pass the check under
    // the dialog — leaving a live prompt over a screen that has already moved on, and a second
    // onAuthSuccess behind it if they then complete it. Saved, because the prompt outlives the
    // composition that raised it: rotating with it on screen recreates this screen underneath, and a
    // flag reset to false there passes the check under a dialog the user is still answering. That is
    // only safe because the prompt is rebuilt below on every composition, which re-registers a
    // callback able to clear it — the restored flag would otherwise have no writer left alive.
    var usingDeviceUnlock by rememberSaveable { mutableStateOf(false) }
    // Null until the camera has been asked. False means no check can run here at all — the hardware
    // reports no face-detection mode, the mode it advertised isn't the one it runs with, or the bind
    // failed — and that is the one state that skips the wait before device unlock is offered, since
    // the answer it is waiting for is already known. Collapsing those cases into one is deliberate:
    // the recovery is identical, and making any of them sit out the timer would leave the user
    // watching a camera that was never going to find them. A StateFlow rather than snapshot state
    // because the capture callback is one of the writers and it does not run on the main thread.
    val cameraUsableFlow = remember { MutableStateFlow<Boolean?>(null) }
    val cameraUsable by cameraUsableFlow.collectAsState()

    // Whether the latest capture result held a face. This is the whole check: the first frame that
    // sets it true passes the gate. Written every frame from a camera thread, but a StateFlow drops
    // a write equal to what it holds, so a steady face or a steady empty frame recomposes nothing.
    val facePresentFlow = remember { MutableStateFlow(false) }
    val facePresent by facePresentFlow.collectAsState()

    // Consecutive results showing no detection at all. Counted rather than acted on at the first,
    // because the results either side of session configuration can carry OFF on a camera that then
    // detects perfectly well.
    val nonDetectingResults = remember { AtomicInteger(0) }
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

    // Rebuilt whenever the activity is, so the prompt's callback is always live — see
    // [rememberDeviceUnlock] for why that is what makes `usingDeviceUnlock` safe to save.
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
        snapshotFlow { facePresent && !usingDeviceUnlock }.first { it }
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
    // no clock at all, leaving Dismiss as the only control on a screen check-out also routes through.
    // Both ends are clamped: saved state can outlive a reboot, which resets the elapsed-time clock.
    LaunchedEffect(Unit) {
        if (searchTimedOut) return@LaunchedEffect
        val spent = SystemClock.elapsedRealtime() - searchStartedAt
        delay((DEVICE_UNLOCK_OFFERED_AFTER_MS - spent).coerceIn(0L, DEVICE_UNLOCK_OFFERED_AFTER_MS))
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
        // CameraX unbinds at ON_STOP and capture results stop arriving, but these values survive it —
        // they are remembered, and a stop is not a config change. A stale `facePresent` is the one
        // that matters: it is reachable while the device-unlock prompt holds the check back, so a
        // gate backgrounded with a face in frame and the prompt then cancelled would pass on a frame
        // from before the camera was even reopened. Clearing them re-imposes the wait for a real
        // result.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                facePresentFlow.value = false
                nonDetectingResults.set(0)
            }
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
            .background(MaterialTheme.colorScheme.background),
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
                            cameraUsableFlow.value = false
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
                            cameraUsableFlow.value = false
                            return@addListener
                        }

                        val preview = faceDetectingPreview(mode) { result ->
                            val faces = result.get(CaptureResult.STATISTICS_FACES).orEmpty()
                            val appliedMode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE)
                            // What the camera advertised was read before the bind; this is what it
                            // actually runs with. A HAL that lists a mode and then reports OFF
                            // returns no faces however long the user waits, so it takes the same
                            // route as one that never offered detection — but only once it has said
                            // so consistently, since the results around session configuration can
                            // carry OFF on a camera that then works perfectly well.
                            if (!FaceDetectSupport.resultIsDetecting(appliedMode, faces.size)) {
                                // A non-detecting result reports nobody by definition, and writing
                                // that is load-bearing: without it a `true` from before the user
                                // walked away survives a run of empty frames, which the device-unlock
                                // prompt holds back long enough to be acted on stale.
                                facePresentFlow.value = false
                                if (nonDetectingResults.incrementAndGet() >=
                                    FaceDetectSupport.NON_DETECTING_RESULTS
                                ) {
                                    cameraUsableFlow.value = false
                                }
                                return@faceDetectingPreview
                            }
                            nonDetectingResults.set(0)
                            // Detection is running after all, so a camera written off by an earlier
                            // run of OFF results is taken back rather than left on the fallback.
                            cameraUsableFlow.value = true

                            facePresentFlow.value = FaceDetectSupport.someonePresent(faces.size)
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
                            cameraUsableFlow.value = true
                        } catch (e: Exception) {
                            // The user is now on a screen whose only control can never enable, so the
                            // fallback has to be offered here rather than left to the failure ladder.
                            Log.e(TAG, "Camera bind failed", e)
                            cameraUsableFlow.value = false
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.presence_dismiss),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

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
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
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
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Withdrawn once the check has passed and while a prompt is outstanding, both to stop a
            // second authentication being raised against a gate that is already leaving — a success
            // never clears `usingDeviceUnlock`, so that half also covers the gap before the caller's
            // state reaches the screen.
            val offerBiometric = successMessage == null &&
                !usingDeviceUnlock &&
                (cameraUsable == false || searchTimedOut)
            if (offerBiometric) {
                if (canUseBiometric) {
                    OutlinedButton(onClick = { launchBiometric() }) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null, // label text conveys the action
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.biometric_use_device_unlock))
                    }
                } else {
                    // The fallback the message names does not exist yet. Handing the user the screen
                    // that creates it keeps the check gated — the alternative on an unusable camera
                    // is a screen offering nothing but Dismiss, and check-out is as gated as
                    // check-in, so the session would stay open until the day boundary wrote a full
                    // day onto a row nothing can edit.
                    OutlinedButton(
                        onClick = {
                            if (!openScreenLockSettings(context)) {
                                errorMessage = unavailableMessage
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null, // label text conveys the action
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.presence_set_up_screen_lock))
                    }
                }
            }
        }
    }
}
