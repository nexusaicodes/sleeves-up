package com.checkin.app.ui.presence

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider

/*
 * What the presence check asks the front camera, with no composition involved: what it can detect,
 * and how to build a preview that reports it. The device-unlock fallback's own device queries live
 * with the rest of it, in `DeviceUnlock.kt`.
 *
 * Separate from the screen because none of it is UI: the screen composes, this asks the device.
 */

private const val TAG = "PresenceHardware"

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
