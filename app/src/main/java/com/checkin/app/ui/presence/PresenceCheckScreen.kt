package com.checkin.app.ui.presence

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PresenceCheckScreen"

/** How long the "you're here" confirmation stays up before the gate hands control back. */
private const val SUCCESS_CONFIRMATION_MS = 800L

private fun deviceUnlockStatus(context: Context): Int =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

// Both Camera2 interop helpers are top-level rather than inline in the composable because
// `@OptIn` does not reach into a lambda or an anonymous object body as far as lint's Java-side
// marker analysis is concerned — annotated there, `UnsafeOptInUsageError` still fires.

/**
 * The face-detection mode the front camera advertises, or null when it offers none — which is also
 * the answer when the characteristics cannot be read at all, since the recovery is identical.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
@Suppress("TooGenericExceptionCaught")
private fun frontCameraDetectMode(provider: ProcessCameraProvider): Int? = try {
    val info = provider.getCameraInfo(CameraSelector.DEFAULT_FRONT_CAMERA)
    FaceDetectSupport.preferredMode(
        Camera2CameraInfo.from(info).getCameraCharacteristic(
            CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES,
        ),
    )
} catch (e: Exception) {
    Log.e(TAG, "Could not read face-detect modes", e)
    null
}

/** A preview that asks the HAL for [mode] and hands every capture result to [onResult]. */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun faceDetectingPreview(mode: Int, onResult: (TotalCaptureResult) -> Unit): Preview {
    val builder = Preview.Builder()
    Camera2Interop.Extender(builder)
        .setCaptureRequestOption(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode)
        .setSessionCaptureCallback(
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) = onResult(result)
            },
        )
    return builder.build()
}

/** False when nothing on the device handles the intent, so the caller can say so instead. */
private fun openScreenLockSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: ActivityNotFoundException) {
    Log.e(TAG, "No screen-lock settings activity", e)
    false
}

/**
 * The check stage of [PresenceGate]: the front camera confirms someone is there, reached once the
 * disclosure and the permission request are behind it.
 *
 * **No image is captured, written or read.** Face detection is done by the camera hardware and
 * arrives as capture-result metadata on the preview stream, so the frames stay in the camera
 * pipeline and the app only ever sees a count. There is no file to delete, nothing to strand if the
 * gate is dismissed mid-check, and nothing to sweep at startup.
 *
 * After [AuthGate.BIOMETRIC_FALLBACK_AFTER] consecutive failures device unlock is offered, and it is
 * offered immediately when the camera cannot run a check at all — no face-detection mode, a mode the
 * HAL then declines to apply, an unavailable provider, or a bind that threw. Those users have no
 * attempt to spend, so putting them through the ladder would leave them on a button that never
 * enables beside a count that never rises, and the fallback would never unlock. [onAuthSuccess]
 * fires once either path passes.
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
    val scope = rememberCoroutineScope()

    // failCount and errorMessage are saved so the 3-attempt budget and the last guidance survive a
    // config change: an attempt lost to a rotation is one that never counts toward the biometric
    // fallback, and on a device whose camera errors reliably that escape hatch is the whole point.
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var failCount by rememberSaveable { mutableIntStateOf(0) }
    // Null until the camera has been asked. False means no check can run here at all — the hardware
    // reports no face-detection mode, the mode it advertised isn't the one it runs with, or the bind
    // failed — and that is the one state that skips the attempt ladder, because a countdown the user
    // cannot win is worse than no countdown. Collapsing those answers into one is deliberate: the
    // recovery is identical, and leaving any of them to the ladder would strand the user on a
    // disabled or unwinnable button with a failure count that never rises, so the fallback would
    // never unlock. A StateFlow rather than snapshot state because the capture callback is one of
    // the writers and it does not run on the main thread.
    val cameraUsableFlow = remember { MutableStateFlow<Boolean?>(null) }
    val cameraUsable by cameraUsableFlow.collectAsState()

    // Set from the camera thread on the first capture result, so a tap reads a real answer rather
    // than the zero the count starts at — bound is not the same as streaming. A StateFlow rather
    // than snapshot state because the writer is not the main thread.
    val streamingFlow = remember { MutableStateFlow(false) }
    val streaming by streamingFlow.collectAsState()

    // Written from a camera thread every frame and read only when the user confirms, so it is an
    // atomic rather than snapshot state — recomposing at the preview frame rate to hold a number
    // nothing draws would be pure waste.
    val faceCount = remember { AtomicInteger(0) }
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

    fun launchBiometric() {
        if (activity == null) {
            errorMessage = context.getString(R.string.biometric_unavailable)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        errorMessage = errString.toString()
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_title))
            .setSubtitle(context.getString(R.string.biometric_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    DisposableEffect(lifecycleOwner) {
        // CameraX unbinds at ON_STOP and capture results stop arriving, but both values survive it —
        // they are remembered, and a stop is not a config change. Left standing, a gate backgrounded
        // with a face in frame comes back with the button already enabled and the old count still
        // reading 1, so a confirm tapped while the camera is reopening passes a check nobody was
        // present for. Clearing them re-imposes the wait for a real result.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                streamingFlow.value = false
                faceCount.set(0)
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
                            // What the camera advertised was read before the bind; this is what it
                            // actually runs with. A HAL that lists a mode and then reports OFF
                            // returns no faces however long the user waits, so it takes the same
                            // route as one that never offered detection rather than an unwinnable
                            // ladder.
                            if (!FaceDetectSupport.isDetecting(result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE))) {
                                cameraUsableFlow.value = false
                            } else {
                                val scores = result.get(CaptureResult.STATISTICS_FACES)
                                    ?.map { it.score }
                                    ?.toIntArray()
                                    ?: IntArray(0)
                                faceCount.set(FaceDetectSupport.facesPresent(scores))
                                streamingFlow.value = true
                            }
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
            } else if (AuthGate.shouldShowHint(failCount)) {
                val remaining = AuthGate.attemptsLeft(failCount)
                Text(
                    text = pluralStringResource(R.plurals.presence_attempts_remaining, remaining, remaining),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                if (cameraUsable != false) {
                    // Disabled until the camera is actually reporting results: bound is not the same
                    // as streaming, and a tap before the first result would read the zero the count
                    // starts at and burn an attempt the user never had.
                    FilledTonalButton(
                        enabled = streaming,
                        onClick = {
                            isProcessing = true
                            errorMessage = null
                            if (faceCount.get() > 0) {
                                // Stay in the processing state through the confirmation delay so the
                                // confirm and biometric buttons can't re-fire before onAuthSuccess.
                                successMessage = context.getString(R.string.presence_face_detected)
                                scope.launch {
                                    delay(SUCCESS_CONFIRMATION_MS)
                                    onAuthSuccess()
                                }
                            } else {
                                isProcessing = false
                                successMessage = null
                                failCount++
                                errorMessage = context.getString(R.string.presence_no_face)
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = stringResource(R.string.presence_confirm),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                val offerBiometric = cameraUsable == false || AuthGate.shouldOfferBiometric(failCount)
                if (offerBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
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
                        // The fallback the message names does not exist yet. Handing the user the
                        // screen that creates it keeps the check gated — the alternative on an
                        // unusable camera is a screen offering nothing but Dismiss, and check-out is
                        // as gated as check-in, so the session would stay open until the day
                        // boundary wrote a full day onto a row nothing can edit.
                        OutlinedButton(
                            onClick = {
                                if (!openScreenLockSettings(context)) {
                                    errorMessage = context.getString(R.string.biometric_unavailable)
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
}
