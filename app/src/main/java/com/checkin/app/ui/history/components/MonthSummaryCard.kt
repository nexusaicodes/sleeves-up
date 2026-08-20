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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.components.charts.ChartGeometry
import com.checkin.app.ui.components.charts.CircularProgressRing
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.dayColor

/**
 * The displayed month in four rings, each one a month figure measured against the user's own
 * all-time equivalent. Raw totals are deliberately absent — a bare "168h" says nothing without
 * knowing how many days produced it, which the average answers.
 *
 * **Every baseline is the user's own record, never a configured number.** There is no target in this
 * app, and a ring measured against one would be that target under another name — so a full ring here
 * means "this month matches your best", not "you cleared a bar". Nothing is drawn in red, and no tile
 * counts down toward a failure: the missed-day count appears only in the showed-up tile's
 * screen-reader description, where it is a fact rather than a verdict on the face of the card.
 *
 * The card carries no heading of its own — its height is a layout constant the calendar grid is
 * sized against — and it does not name the month either; the month selector directly above the
 * calendar states that at `titleLarge`.
 */
@Composable
fun MonthSummaryCard(
    summaries: Map<String, DailyAggregate>,
    trackedDaysInMonth: Int,
    monthBestStreak: Int,
    allTimeBestStreak: Int,
    allTimeAvgDailyMs: Long,
    allTimePeakDayMs: Long,
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
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                StatRing(
                    value = "${tiles.showedUp}",
                    label = stringResource(R.string.stat_showed_up),
                    baseline = pluralStringResource(
                        R.plurals.stat_baseline_of_days,
                        trackedDaysInMonth,
                        trackedDaysInMonth,
                    ),
                    progress = statRatio(tiles.showedUp, trackedDaysInMonth),
                    // Two counts, so neither can be the plural's own quantity: each is worded
                    // through `days_count` first and arrives here as a phrase.
                    contentDescription = stringResource(
                        R.string.cd_month_split,
                        pluralStringResource(R.plurals.days_count, tiles.showedUp, tiles.showedUp),
                        pluralStringResource(R.plurals.days_count, tiles.missed, tiles.missed),
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatRing(
                    value = "$monthBestStreak",
                    label = stringResource(R.string.stat_best_streak),
                    baseline = stringResource(R.string.stat_baseline_all_time, "$allTimeBestStreak"),
                    progress = statRatio(monthBestStreak, allTimeBestStreak),
                    contentDescription = stringResource(
                        R.string.cd_stat_vs_all_time,
                        stringResource(R.string.stat_best_streak),
                        "$monthBestStreak",
                        "$allTimeBestStreak",
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                StatRing(
                    value = formatDuration(tiles.avgDailyMs),
                    label = stringResource(R.string.stat_avg_per_day),
                    baseline = stringResource(
                        R.string.stat_baseline_all_time,
                        formatDuration(allTimeAvgDailyMs),
                    ),
                    progress = statRatio(tiles.avgDailyMs, allTimeAvgDailyMs),
                    contentDescription = stringResource(
                        R.string.cd_stat_vs_all_time,
                        stringResource(R.string.stat_avg_per_day),
                        formatDuration(tiles.avgDailyMs),
                        formatDuration(allTimeAvgDailyMs),
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatRing(
                    value = formatDuration(tiles.peakDayMs),
                    label = stringResource(R.string.stat_longest_day),
                    baseline = stringResource(
                        R.string.stat_baseline_all_time,
                        formatDuration(allTimePeakDayMs),
                    ),
                    progress = statRatio(tiles.peakDayMs, allTimePeakDayMs),
                    contentDescription = stringResource(
                        R.string.cd_stat_vs_all_time,
                        stringResource(R.string.stat_longest_day),
                        formatDuration(tiles.peakDayMs),
                        formatDuration(allTimePeakDayMs),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val TILE_SPACING = 12.dp

/**
 * Ring diameter, grown with the user's font scale and capped.
 *
 * The value sits inside the ring in `sp` while the ring would otherwise be a fixed `dp`, so raising
 * the system font size shrinks the hole in real terms until the value no longer fits it. The cap
 * stops two tiles per row from outgrowing a narrow screen; past it the value wraps and then
 * ellipsizes rather than being clipped mid-word.
 */
private val RING_SIZE = 88.dp
private val RING_MAX_SIZE = 104.dp

/** Near a tenth of the diameter: a band rather than a hairline, without eating the hole. */
private val RING_STROKE = 9.dp

/**
 * One stat: a value inside a ring filled to [progress], named underneath, over the baseline the ring
 * measures it against.
 *
 * The whole tile announces itself once, through [contentDescription] on the ring — the fill is a
 * ratio and colour carries none of it to a screen reader, while the value and labels below would
 * otherwise repeat the same figures a second time.
 */
@Composable
private fun StatRing(
    value: String,
    label: String,
    baseline: String,
    progress: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val diameter = (RING_SIZE * LocalDensity.current.fontScale).coerceIn(RING_SIZE, RING_MAX_SIZE)
    // The hole is round, so only the square inscribed in it is usable for the value.
    val innerBound = (diameter - RING_STROKE * 2) * ChartGeometry.INSCRIBED_SQUARE

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressRing(
            progress = progress,
            color = dayColor(),
            // The track has to read as a ring in its own right — an empty month is a grey circle,
            // not a blank space. Must stay `outline`: `outlineVariant` *is* `surfaceVariant` in the
            // dark scheme, which is this card's own container, so the track would vanish into it.
            trackColor = MaterialTheme.colorScheme.outline,
            contentDescription = contentDescription,
            modifier = Modifier.size(diameter),
            strokeWidth = RING_STROKE,
        ) {
            Box(
                modifier = Modifier.size(innerBound).clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

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
            Text(
                text = baseline,
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
            "2026-06-02" to DailyAggregate("2026-06-02", 8 * 3_600_000L, 1, 0L, 0L),
            "2026-06-03" to DailyAggregate("2026-06-03", 45 * 60_000L, 1, 0L, 0L),
        )
        MonthSummaryCard(
            summaries = summaries,
            trackedDaysInMonth = 5,
            monthBestStreak = 2,
            allTimeBestStreak = 9,
            allTimeAvgDailyMs = 6 * 3_600_000L,
            allTimePeakDayMs = 11 * 3_600_000L,
            formatDuration = { "${it / 3_600_000}h" },
        )
    }
}

/** Nothing tracked yet: every ring is a bare track, which is the honest reading of an empty month. */
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthSummaryCardEmptyPreview() {
    CheckInAppTheme {
        MonthSummaryCard(
            summaries = emptyMap(),
            trackedDaysInMonth = 0,
            monthBestStreak = 0,
            allTimeBestStreak = 0,
            allTimeAvgDailyMs = 0L,
            allTimePeakDayMs = 0L,
            formatDuration = { "0h" },
        )
    }
}
