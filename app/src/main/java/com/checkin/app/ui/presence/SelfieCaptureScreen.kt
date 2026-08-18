package com.checkin.app.ui.presence

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.checkin.app.CheckInApplication
import com.checkin.app.R
import com.checkin.app.platform.SelfieStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SelfieCaptureScreen"

/** How long the "face detected" confirmation stays up before the gate hands control back. */
private const val SUCCESS_CONFIRMATION_MS = 800L

/**
 * The capture stage of [PresenceGate]: a front-camera frame verified by on-device face detection,
 * reached once the disclosure and the permission request are behind it. The captured image is
 * transient — it is deleted as soon as the outcome is known. After [AuthGate.BIOMETRIC_FALLBACK_AFTER]
 * consecutive failures, device unlock is offered as a fallback. [onAuthSuccess] fires once either
 * path passes.
 */
// Binding the camera fails as whatever the vendor implementation throws — an unavailable front
// camera, a lifecycle race, a device-specific fault. There is one recovery for all of them: log and
// leave the preview blank, which the gate already renders as a failed attempt.
@Suppress("TooGenericExceptionCaught")
@Composable
fun SelfieCaptureScreen(onAuthSuccess: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // App-scoped: the detect+delete work and the outcome it produces must outlive a mid-capture
    // dismiss — the JPEG must never be stranded and the failure count must never be dropped. It is
    // cancelled only on process death, not when the gate leaves composition.
    val appScope = (context.applicationContext as CheckInApplication).container.applicationScope

    // failCount and errorMessage are saved so the 3-attempt budget and the last guidance survive a
    // config change: an attempt lost to a rotation is one that never counts toward the biometric
    // fallback, and on a device whose camera errors reliably that escape hatch is the whole point.
    // successMessage/isProcessing are intentionally not saved — their 800ms confirmation coroutine
    // is composition-scoped and dies on recreation.
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var failCount by rememberSaveable { mutableIntStateOf(0) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Tracks disposal so the async provider callback can release the camera if the gate is gone
    // before the provider resolves (onDispose would otherwise see a null provider and skip unbind).
    val gateDisposed = remember { AtomicBoolean(false) }

    val canUseBiometric = remember {
        activity != null &&
            BiometricManager.from(context)
                .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

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

    DisposableEffect(Unit) {
        onDispose {
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
                        val provider = cameraProviderFuture.get()
                        if (gateDisposed.get()) {
                            // Gate left composition before the provider resolved — release the camera
                            // now instead of binding one that nothing remains to unbind.
                            provider.unbindAll()
                            return@addListener
                        }
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                imageCapture,
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera bind failed", e)
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
                contentDescription = stringResource(R.string.selfie_dismiss),
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
                    // Announce the capture outcome to TalkBack as it changes.
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

            if (AuthGate.shouldShowHint(failCount) && canUseBiometric) {
                val remaining = AuthGate.attemptsLeft(failCount)
                Text(
                    text = pluralStringResource(R.plurals.selfie_attempts_remaining, remaining, remaining),
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
                // Disabled until the camera binds, so an eager pre-bind tap can't error out and burn
                // a failure attempt toward the biometric fallback.
                FilledTonalButton(
                    enabled = cameraProvider != null,
                    onClick = {
                        isProcessing = true
                        errorMessage = null
                        captureAndValidate(
                            context = context,
                            imageCapture = imageCapture,
                            appScope = appScope,
                        ) { outcome ->
                            when (outcome) {
                                CaptureOutcome.FACE_FOUND -> {
                                    // Stay in the processing state through the confirmation delay so
                                    // the capture and biometric buttons can't re-fire before
                                    // onAuthSuccess runs.
                                    errorMessage = null
                                    successMessage = context.getString(R.string.selfie_face_detected)
                                    scope.launch {
                                        delay(SUCCESS_CONFIRMATION_MS)
                                        onAuthSuccess()
                                    }
                                }
                                // A miss and an error are counted alike, so a camera that keeps
                                // erroring still escalates to the device-unlock fallback.
                                CaptureOutcome.NO_FACE, CaptureOutcome.ERROR -> {
                                    isProcessing = false
                                    successMessage = null
                                    failCount++
                                    errorMessage = context.getString(
                                        if (outcome == CaptureOutcome.NO_FACE) {
                                            R.string.selfie_no_face
                                        } else {
                                            R.string.selfie_error
                                        },
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.selfie_capture),
                        modifier = Modifier.size(32.dp),
                    )
                }

                if (AuthGate.shouldOfferBiometric(failCount) && canUseBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { launchBiometric() }) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null, // label text conveys the action
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.biometric_use_device_unlock))
                    }
                }
            }
        }
    }
}

/** How a single presence capture resolved. Every outcome ends with the frame already deleted. */
enum class CaptureOutcome {
    /** A face was found in the frame. */
    FACE_FOUND,

    /** The frame was readable but held no face. */
    NO_FACE,

    /** The capture or the detection itself failed; indistinguishable to the user from a miss. */
    ERROR,
}

// The camera pipeline surfaces failures as whatever the vendor implementation throws, and every one
// of them means the same thing here: this attempt did not produce a usable frame.
@Suppress("TooGenericExceptionCaught")
private fun captureAndValidate(
    context: Context,
    imageCapture: ImageCapture,
    appScope: CoroutineScope,
    onResult: (CaptureOutcome) -> Unit,
) {
    val outputFile = File(SelfieStorage.dir(context), "${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Every step runs on the app scope: a mid-capture dismiss or config change must not
                // cancel the detect+delete and strand the JPEG, and must not drop the outcome either —
                // [onResult] advances the failure count that escalates to the biometric fallback, and
                // that count is saved state, not composition state.
                appScope.launch(Dispatchers.IO) {
                    val outcome = try {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        if (bitmap == null) {
                            CaptureOutcome.ERROR
                        } else {
                            val found = FaceDetectionHelper.detectFace(bitmap)
                            bitmap.recycle()
                            if (found) CaptureOutcome.FACE_FOUND else CaptureOutcome.NO_FACE
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Face detection failed", e)
                        CaptureOutcome.ERROR
                    } finally {
                        // Selfies are a transient auth gate — never persisted.
                        outputFile.delete()
                    }
                    withContext(Dispatchers.Main) { onResult(outcome) }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                // Already on the main executor, so the outcome is delivered inline; only the cleanup
                // of the partially written frame is handed to the app scope, off the UI thread.
                onResult(CaptureOutcome.ERROR)
                appScope.launch(Dispatchers.IO) { outputFile.delete() }
            }
        },
    )
}
