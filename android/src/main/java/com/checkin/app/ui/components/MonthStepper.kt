package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adevinta.spark.components.text.Text
import com.checkin.app.R
import com.checkin.app.ui.theme.LocalAppTypography

/**
 * A month heading between a back and a forward arrow — History's and Reports' one month control.
 *
 * The label takes the row's remaining width and may wrap to two lines: at a large font scale a
 * single line clips "August 2026" to "Augus…", and a truncated month is not a date anyone can read.
 * The arrows keep their slot when disabled, so the row never reflows around them.
 */
@Composable
fun MonthStepper(
    label: String,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = AppIcons.ChevronStart,
            contentDescription = stringResource(R.string.previous_month),
            onClick = onPrevious,
            enabled = previousEnabled,
        )
        Text(
            text = label,
            style = LocalAppTypography.current.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AppIconButton(
            icon = AppIcons.ChevronEnd,
            contentDescription = stringResource(R.string.next_month),
            onClick = onNext,
            enabled = nextEnabled,
        )
    }
}
