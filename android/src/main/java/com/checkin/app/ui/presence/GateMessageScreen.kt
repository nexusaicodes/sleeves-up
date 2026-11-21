package com.checkin.app.ui.presence

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Ghost
import com.adevinta.spark.components.buttons.Primary
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.R
import com.checkin.app.ui.components.FullScreenMessage
import com.checkin.app.ui.components.MESSAGE_BODY_TO_ACTION_GAP
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
    icon: SparkIcon,
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
        Spacer(modifier = Modifier.height(MESSAGE_BODY_TO_ACTION_GAP))
        Button.Primary(onClick = onAction, text = actionLabel)
        Button.Ghost(onClick = onDismiss, text = stringResource(R.string.presence_gate_cancel))
    }
}
