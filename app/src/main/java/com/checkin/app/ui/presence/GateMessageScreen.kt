package com.checkin.app.ui.presence

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.ui.components.FullScreenMessage
import com.checkin.app.ui.components.MessageIcon

/**
 * Every full-screen stop the presence gate can make: the shared message, plus one primary action and
 * a way out.
 *
 * The disclosure and the camera-recovery screen are the same words apart, and both are drawn from
 * here so a change to either lands on both.
 */
@Composable
fun GateMessageScreen(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenMessage(
        icon = { MessageIcon(icon) },
        title = title,
        message = message,
    ) {
        Spacer(modifier = Modifier.height(BODY_TO_ACTION_GAP))
        Button(onClick = onAction) {
            Text(actionLabel)
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.presence_gate_cancel))
        }
    }
}

private val BODY_TO_ACTION_GAP = 24.dp
