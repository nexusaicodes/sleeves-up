package com.checkin.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.card.Card
import com.adevinta.spark.components.divider.DividerIntent
import com.adevinta.spark.components.divider.HorizontalDivider
import com.adevinta.spark.components.text.Text
import com.checkin.app.ui.theme.LocalAppTypography

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
    // Flat, never Highlight: a highlighted card's border is the accent, vermilion, which the app
    // never draws as a verdict.
    Card.Flat(
        modifier = modifier.fillMaxWidth(),
        colors = SparkTheme.colors.backgroundVariant,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = title,
            style = LocalAppTypography.current.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = SparkTheme.typography.small,
                color = SparkTheme.colors.onBackgroundVariant,
            )
        }
        Spacer(modifier = Modifier.height(contentSpacing))
        content()
    }
}

/**
 * A rule between groups of rows inside a [SectionCard], with its own spacing.
 *
 * Spark's divider in its `Outline` intent, at half alpha: a rule rather than a border, close to
 * mudda's hairline. Never `OutlineHigh`, the ink the text is drawn in — the heaviest line in the
 * app, louder than the rows it separates.
 */
@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .alpha(0.5f),
        intent = DividerIntent.Outline,
    )
}
