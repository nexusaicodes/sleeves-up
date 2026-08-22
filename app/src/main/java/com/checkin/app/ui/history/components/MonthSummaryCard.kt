package com.checkin.app.ui.history.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.components.charts.ChartGeometry
import com.checkin.app.ui.components.charts.CircularProgressRing
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.dayColor
import java.util.Locale

/**
 * The displayed month: days shown up as the hero, with two descriptions of the days underneath.
 *
 * **One figure fills a ring, and it is the only one that may.** Days shown up is measured against the
 * month's own tracked days — a bounded denominator the calendar itself sets — so the arc is a real
 * fraction of something external. The two tiles below carry a ring as a *frame* and nothing more:
 * an average has no natural denominator, and the only one available would be the user's own all-time
 * average, which is a personal best under another name. Filling an arc against that ratchets — beat
 * it once and every ordinary month behind it renders as a partial version of the good one.
 *
 * Two tiles were removed for exactly that reason and must not come back: the month's longest day and
 * its best run of consecutive days, each ringed against its all-time equivalent. Hours appear here as
 * a quantity and nowhere as a rank.
 *
 * The card carries no heading of its own — its height is a layout constant the calendar grid is
 * sized against — and it does not name the month either; the month selector directly above the
 * calendar states that at `titleLarge`.
 */
@Composable
fun MonthSummaryCard(
    summaries: Map<String, DailyAggregate>,
    trackedDaysInMonth: Int,
    formatDuration: (Long) -> String,
) {
    val tiles = computeMonthTiles(summaries, trackedDaysInMonth)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroTile(
                value = "${tiles.showedUp}",
                label = stringResource(R.string.stat_showed_up),
                caption = pluralStringResource(
                    R.plurals.stat_baseline_of_days,
                    trackedDaysInMonth,
                    trackedDaysInMonth,
                ),
                progress = showedUpRatio(tiles.showedUp, trackedDaysInMonth),
                // Two counts, so neither can be the plural's own quantity: each is worded through
                // `days_count` first and arrives here as a phrase.
                contentDescription = stringResource(
                    R.string.cd_month_split,
                    pluralStringResource(R.plurals.days_count, tiles.showedUp, tiles.showedUp),
                    pluralStringResource(R.plurals.days_count, tiles.missed, tiles.missed),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                FrameTile(
                    value = formatDuration(tiles.avgDailyMs),
                    label = stringResource(R.string.stat_avg_per_day),
                    modifier = Modifier.weight(1f),
                )
                FrameTile(
                    value = formatSessionsPerDay(tiles.avgSessionsPerDay),
                    label = stringResource(R.string.stat_sessions_per_day),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One decimal place: the figure is a rhythm, and a second digit implies a precision it lacks. */
private fun formatSessionsPerDay(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

private val TILE_SPACING = 12.dp

/**
 * Diameters, grown with the user's font scale and capped.
 *
 * The value sits inside the ring in `sp` while the ring would otherwise be a fixed `dp`, so raising
 * the system font size shrinks the hole in real terms until the value no longer fits it. The caps
 * stop the row of two from outgrowing a narrow screen; past them the value wraps and then ellipsizes
 * rather than being clipped mid-word.
 */
private val HERO_SIZE = 104.dp
private val HERO_MAX_SIZE = 124.dp
private val FRAME_SIZE = 76.dp
private val FRAME_MAX_SIZE = 92.dp

/** Near a tenth of the hero's diameter: a band rather than a hairline, without eating the hole. */
private val HERO_STROKE = 10.dp

/**
 * The frame tiles' ring is deliberately thin.
 *
 * It has to read as an outline around a number rather than as a gauge sitting at zero, which is what
 * a band of the hero's weight with no arc on it would look like. Same reason it is drawn in
 * `outline` rather than in the day hue: colour here would imply the figure is being measured.
 */
private val FRAME_STROKE = 3.dp

@Composable
private fun scaledSize(base: Dp, max: Dp): Dp = (base * LocalDensity.current.fontScale).coerceIn(base, max)

/**
 * Days shown up, ringed against the month's tracked days.
 *
 * The whole tile announces itself once, through [contentDescription] on the ring — the fill is a
 * ratio and colour carries none of it to a screen reader, while the value and labels below would
 * otherwise repeat the same figures a second time.
 */
@Composable
private fun HeroTile(value: String, label: String, caption: String, progress: Float, contentDescription: String) {
    val diameter = scaledSize(HERO_SIZE, HERO_MAX_SIZE)
    // The hole is round, so only the square inscribed in it is usable for the value.
    val innerBound = (diameter - HERO_STROKE * 2) * ChartGeometry.INSCRIBED_SQUARE

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressRing(
            progress = progress,
            color = dayColor(),
            // The track has to read as a ring in its own right — an empty month is a grey circle,
            // not a blank space. Must stay `outline`: `outlineVariant` *is* `surfaceVariant` in the
            // dark scheme, which is this card's own container, so the track would vanish into it.
            trackColor = MaterialTheme.colorScheme.outline,
            contentDescription = contentDescription,
            modifier = Modifier.size(diameter),
            strokeWidth = HERO_STROKE,
        ) {
            TileValue(value, innerBound, MaterialTheme.typography.titleLarge)
        }
        TileLabels(label = label, caption = caption)
    }
}

/**
 * A figure inside a ring that measures nothing: the ring is a frame, drawn at zero progress in the
 * neutral outline colour so it cannot be read as an arc that failed to fill.
 *
 * There is no baseline caption here on purpose. Every candidate was the user's own all-time figure,
 * which is the comparison this card exists without.
 */
@Composable
private fun FrameTile(value: String, label: String, modifier: Modifier = Modifier) {
    val diameter = scaledSize(FRAME_SIZE, FRAME_MAX_SIZE)
    val innerBound = (diameter - FRAME_STROKE * 2) * ChartGeometry.INSCRIBED_SQUARE
    val outline = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressRing(
            progress = 0f,
            // Never drawn — a zero sweep renders no arc — but the parameter is not nullable, and the
            // track colour is the honest answer to "what colour is the arc that isn't there".
            color = outline,
            trackColor = outline,
            contentDescription = stringResource(R.string.cd_month_stat, label, value),
            modifier = Modifier.size(diameter),
            strokeWidth = FRAME_STROKE,
        ) {
            TileValue(value, innerBound, MaterialTheme.typography.titleSmall)
        }
        TileLabels(label = label, caption = null)
    }
}

/**
 * The figure inside a ring, bounded to the square inscribed in the hole.
 *
 * Takes a whole [style] rather than a size: the two tiles are set at different type scales, and
 * pairing one scale's font size with another's line height clips the taller of the two.
 */
@Composable
private fun TileValue(value: String, innerBound: Dp, style: TextStyle) {
    Box(
        modifier = Modifier.size(innerBound).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            style = style,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TileLabels(label: String, caption: String?) {
    Column(
        modifier = Modifier.padding(top = 6.dp).clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthSummaryCardPreview() {
    CheckInAppTheme {
        val summaries = mapOf(
            "2026-06-02" to DailyAggregate("2026-06-02", 8 * 3_600_000L, 2, 0L, 0L, 0),
            "2026-06-03" to DailyAggregate("2026-06-03", 45 * 60_000L, 1, 0L, 0L, 0),
        )
        MonthSummaryCard(
            summaries = summaries,
            trackedDaysInMonth = 5,
            formatDuration = { "${it / 3_600_000}h" },
        )
    }
}

/** Nothing tracked yet: a bare track and two zeros, which is the honest reading of an empty month. */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthSummaryCardEmptyPreview() {
    CheckInAppTheme {
        MonthSummaryCard(
            summaries = emptyMap(),
            trackedDaysInMonth = 0,
            formatDuration = { "0h" },
        )
    }
}
