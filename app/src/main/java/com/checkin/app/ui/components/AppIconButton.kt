package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adevinta.spark.components.iconbuttons.IconButtonGhost
import com.adevinta.spark.components.iconbuttons.IconButtonIntent
import com.adevinta.spark.icons.SparkIcon

/**
 * Spark's ghost icon button, centred in the app's 48dp touch target.
 *
 * Spark lays an icon button out at 44dp. The platform widens a smaller touch target to 48dp at the
 * input layer, but only into space the layout leaves free, so the button sits centred in a box of
 * at least 48dp. The size cannot go on the button's own modifier: Spark applies that to an outer box
 * and pins the button to its top-start corner. [contentDescription] is required, since an icon alone
 * names nothing to a screen reader, and Spark also shows it as the button's long-press tooltip.
 */
@Composable
fun AppIconButton(
    icon: SparkIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    intent: IconButtonIntent = IconButtonIntent.Main,
) {
    Box(
        modifier = modifier.sizeIn(minWidth = TOUCH_TARGET, minHeight = TOUCH_TARGET),
        contentAlignment = Alignment.Center,
    ) {
        IconButtonGhost(
            icon = icon,
            onClick = onClick,
            enabled = enabled,
            intent = intent,
            contentDescription = contentDescription,
        )
    }
}

private val TOUCH_TARGET = 48.dp
