package com.checkin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.tokens.highlight

/**
 * A [SectionCard] holding a chart: the same card, with the wider gap under the heading that a plot
 * needs and an optional [subtitle] for the span or unit the chart covers.
 *
 * Its own name rather than a spacing argument at seven call sites, and a delegation rather than a
 * second Card, so a chart card cannot drift from every other card in padding or shape.
 *
 * The three small pieces such a card is built out of — [LegendRow], [AxisLabel] and [StatsRow] —
 * live below it, so the card and its parts are one file to open rather than a name to guess at.
 */
@Composable
fun ChartCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title = title, subtitle = subtitle, contentSpacing = CHART_CONTENT_SPACING, content = content)
}

private val CHART_CONTENT_SPACING = 16.dp

/**
 * A swatch, a label and its count, beside a [com.checkin.app.ui.components.charts.DonutChart].
 *
 * **The label gives way, never the count**, as in [StatsRow]: the count is measured first and the
 * label wraps into what is left, so at a large font scale beside the ring a long label takes a second
 * line instead of squeezing the count out of the row.
 */
@Composable
fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, SparkTheme.shapes.full))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = SparkTheme.typography.body2,
            color = SparkTheme.colors.onBackgroundVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$count", style = SparkTheme.typography.body2.highlight, maxLines = 1)
    }
}

/** A chart's axis label: caption, in the muted text colour, the same size as the figures beside it. */
@Composable
fun AxisLabel(text: String) {
    Text(
        text = text,
        style = SparkTheme.typography.caption,
        color = SparkTheme.colors.onBackgroundVariant,
    )
}

/**
 * One label-value line of the stats table.
 *
 * **The label is the half that gives way, never the value.** Under a plain `SpaceBetween` the label
 * is measured first and wins the width, so at a 2x font scale a long label squeezes "2.2" into a
 * vertical `2` `.` `2`. A wrapped number stops reading as a number; a wrapped label is merely a label
 * on two lines.
 *
 * So the value is unweighted (measured first, taking what it needs on one line) and the label is
 * weighted (taking the remainder and wrapping into it) — the same order of sacrifice as
 * [SessionIntervalRow].
 */
@Composable
fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SparkTheme.typography.body2,
            color = SparkTheme.colors.onBackgroundVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = SparkTheme.typography.body2.highlight,
            maxLines = 1,
        )
    }
}
