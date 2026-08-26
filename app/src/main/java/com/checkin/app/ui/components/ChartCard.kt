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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A [SectionCard] holding a chart: the same card, with the wider gap under the heading that a plot
 * needs and an optional [subtitle] for the span or unit the chart covers.
 *
 * Its own name rather than a spacing argument at seven call sites, and a delegation rather than a
 * second Card — the two were separate copies that had already drifted 4dp apart, which is how a card
 * comes to look slightly different depending on which tab you meet it on.
 *
 * The three small pieces such a card is built out of — [LegendRow], [AxisLabel] and [StatsRow] —
 * live below it, so the card and its parts are one file to open rather than a name to guess at.
 */
@Composable
fun ChartCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title = title, subtitle = subtitle, contentSpacing = CHART_CONTENT_SPACING, content = content)
}

private val CHART_CONTENT_SPACING = 16.dp

@Composable
fun LegendRow(color: Color, label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One label-value line of the stats table.
 *
 * **The label is the half that gives way, never the value.** Under a plain `SpaceBetween` both
 * children compete for the width and the label wins, because it is measured first — at a 2x font
 * scale "Sessions per day showed up" left its value wrapping to three lines of one character each,
 * so "2.2" rendered as a vertical `2` `.` `2`. A wrapped number stops reading as a number at all,
 * where a wrapped label is merely a label on two lines.
 *
 * The value is therefore unweighted (it is measured first and takes what it needs, on one line) and
 * the label is weighted (it takes the remainder and wraps into it). Same order of sacrifice as the
 * session ledger row, for the same reason.
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
