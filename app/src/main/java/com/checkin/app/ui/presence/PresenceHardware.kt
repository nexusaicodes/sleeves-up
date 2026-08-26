package com.checkin.app.ui.presence

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.provider.Settings
import android.util.Log
import androidx.annotation.OptIn
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider

/*
 * What the presence check asks the device, with no composition involved: what the front camera can
 * detect, how to build a preview that reports it, whether a device unlock exists to fall back to,
 * and how to reach the settings screen that creates one.
 *
 * Separate from the screen because none of it is UI, and because the screen was 563 lines under a
 * name that announced only the last of its jobs.
 */

private const val TAG = "PresenceHardware"

/**
 * Whether the device has a credential to fall back to. Re-read on resume by the screen, never
 * cached at first composition: the recovery for "none enrolled" is a round trip through system
 * settings, which returns no result and recreates no activity.
 */
internal fun deviceUnlockStatus(context: Context): Int =
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
internal fun frontCameraDetectMode(provider: ProcessCameraProvider): Int? = try {
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
internal fun faceDetectingPreview(mode: Int, onResult: (TotalCaptureResult) -> Unit): Preview {
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
internal fun openScreenLockSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: ActivityNotFoundException) {
    Log.e(TAG, "No screen-lock settings activity", e)
    false
}
