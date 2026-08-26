package com.checkin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

/*
 * The three small pieces a chart card is built out of: a legend entry, an axis label, and a
 * label-value line.
 *
 * They lived private inside ReportsScreen, which is where a second caller would never have found
 * them -- and StatsRow's sacrifice rule below is already the same rule SessionIntervalRow states,
 * so it existed twice with one copy unreachable.
 */

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
