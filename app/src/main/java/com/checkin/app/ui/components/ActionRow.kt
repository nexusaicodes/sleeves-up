package com.checkin.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A tappable row inside a [SectionCard]: a label, an optional [value] beneath it stating the current
 * setting, and a trailing icon for where it leads. A row that is not [enabled] dims and ignores taps.
 *
 * Shared across Settings, Habits and About because the 48dp tap target below is the kind of rule a
 * screen needing a row like this would otherwise reinvent without.
 */
@Composable
fun ActionRow(
    label: String,
    icon: ImageVector,
    contentDescription: String?,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            // Disabled reads as dimmed, the way Material draws a disabled control's content.
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            // 48dp of height is the minimum tap target; the vertical padding gets it there without
            // pinning the row, so it still grows with a large font scale.
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
