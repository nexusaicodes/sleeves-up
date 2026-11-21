package com.checkin.app.ui.presence

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.R

/**
 * Play-policy prominent disclosure for the camera permission. Rendered full-screen before the CAMERA
 * runtime prompt is ever raised, so the user affirmatively consents to on-device presence
 * verification before the camera can be accessed. [onAccept] advances to the system permission
 * request; [onDismiss] abandons the check that triggered it.
 */
@Composable
fun CameraDisclosureScreen(onAccept: () -> Unit, onDismiss: () -> Unit) {
    GateMessageScreen(
        icon = SparkIcon.Vector(Icons.Rounded.Face),
        title = stringResource(R.string.camera_disclosure_title),
        message = stringResource(R.string.camera_disclosure_message),
        actionLabel = stringResource(R.string.camera_disclosure_button),
        onAction = onAccept,
        onDismiss = onDismiss,
    )
}
