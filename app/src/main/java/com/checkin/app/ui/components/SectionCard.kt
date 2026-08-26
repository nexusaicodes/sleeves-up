package com.checkin.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A titled block on a settings-style list. The heading is a plain [Text] rather than a top app bar
 * title — the nav scaffold owns that — so these stack inside a scrolling list.
 *
 * [contentSpacing] is the gap under the heading. It is a parameter rather than a constant because a
 * card holding a chart wants more air under its title than a card holding rows does — see
 * [ChartCard], which is this card with that one value fixed.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(contentSpacing))
            content()
        }
    }
}

/**
 * A [SectionCard] holding a chart: the same card, with the wider gap under the heading that a plot
 * needs and an optional [subtitle] for the span or unit the chart covers.
 *
 * Its own name rather than a spacing argument at seven call sites, and a delegation rather than a
 * second Card — the two were separate copies that had already drifted 4dp apart, which is how a card
 * comes to look slightly different depending on which tab you meet it on.
 */
@Composable
fun ChartCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title = title, subtitle = subtitle, contentSpacing = CHART_CONTENT_SPACING, content = content)
}

private val CHART_CONTENT_SPACING = 16.dp

/**
 * A rule between groups of rows inside a [SectionCard], with its own spacing.
 *
 * Never a bare `HorizontalDivider`: that defaults to `outlineVariant`, which in the Material 3
 * baseline dark palette is the same value as the `surfaceVariant` this card fills with — 1.00:1, an
 * invisible line whose margins read as an unexplained gap. `outline` clears it in both themes
 * (2.96:1 dark, 3.48:1 light) while staying quieter than any text on the card.
 */
@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outline,
    )
}
