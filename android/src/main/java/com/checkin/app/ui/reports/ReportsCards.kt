package com.checkin.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.text.Text
import com.adevinta.spark.tokens.highlight
import com.checkin.app.R
import com.checkin.app.data.SessionBand
import com.checkin.app.data.StartBucket
import com.checkin.app.ui.components.AxisLabel
import com.checkin.app.ui.components.ChartCard
import com.checkin.app.ui.components.LegendRow
import com.checkin.app.ui.components.SectionDivider
import com.checkin.app.ui.components.StatsRow
import com.checkin.app.ui.components.charts.BarChart
import com.checkin.app.ui.components.charts.DonutChart
import com.checkin.app.ui.components.charts.DonutChartDefaults
import com.checkin.app.ui.components.charts.LineChart
import com.checkin.app.ui.theme.LocalTimePatterns
import com.checkin.app.ui.theme.dayColor
import com.checkin.app.ui.theme.descriptiveHues
import com.checkin.app.util.TimeFormat
import com.checkin.app.util.withLatinDigits
import java.util.Locale

/*
 * The cards the Reports screen stacks under its scope selector. Each reads one [ReportsUiState] and
 * draws one figure or chart; none names a span, since the selector states the scope and nothing
 * else may.
 */

private const val MILLIS_PER_HOUR = 3_600_000f

/**
 * Every scalar the scope produces, as one dense table.
 *
 * Every scalar is stated once, here, and the charts below carry only what a chart adds. Spread
 * across the holes and legends instead, a figure renders twice with nothing added the second time.
 *
 * **Every row is a quantity and none is a rank.** The two averages are rows rather than rings
 * because a row states a figure, where a ring measures it against a denominator these do not have —
 * the only one available is the user's own all-time equivalent, a personal best under another name.
 */
@Composable
internal fun ScopeStatsCard(uiState: ReportsUiState) {
    val patterns = LocalTimePatterns.current
    ChartCard(title = stringResource(R.string.scope_stats_title)) {
        // Only meaningful under all time; a month scope's own start is the month, which the selector
        // states directly above.
        if (uiState.scope is ReportScope.AllTime) {
            uiState.trackingStartDate?.let { start ->
                StatsRow(stringResource(R.string.stat_tracking_since), TimeFormat.dateWithYear(start, patterns))
            }
        }
        StatsRow(
            stringResource(R.string.stat_tracked_days),
            pluralStringResource(R.plurals.days_count, uiState.totalDays, uiState.totalDays),
        )
        StatsRow(
            stringResource(R.string.stat_showed_up),
            pluralStringResource(R.plurals.days_count, uiState.showedUpDays, uiState.showedUpDays),
        )
        StatsRow(
            stringResource(R.string.stat_missed),
            pluralStringResource(R.plurals.days_count, uiState.missedDays, uiState.missedDays),
        )
        SectionDivider()
        // A total, and nothing measures it.
        StatsRow(stringResource(R.string.stat_total_hours), TimeFormat.durationShort(uiState.totalWorkedMs, patterns))
        StatsRow(
            stringResource(R.string.stat_total_sessions),
            pluralStringResource(R.plurals.sessions_count, uiState.totalSessions, uiState.totalSessions),
        )
        StatsRow(stringResource(R.string.stat_avg_per_day), TimeFormat.durationShort(uiState.avgDailyMs, patterns))
        StatsRow(stringResource(R.string.stat_sessions_per_day), formatAverage(uiState.avgSessionsPerDay))
    }
}

@Composable
internal fun DailyHoursCard(uiState: ReportsUiState) {
    val patterns = LocalTimePatterns.current
    val hours = uiState.dailySeries.map { it.workedMs / MILLIS_PER_HOUR }
    // The chart's own mean, not a configured bar: it shows where the window sits relative to itself
    // rather than against a target a day could fall short of.
    val average = if (hours.isEmpty()) 0f else hours.sum() / hours.size

    ChartCard(
        title = stringResource(R.string.chart_daily_hours_title),
        // The dashed line needs naming somewhere; the subtitle keeps it out of the date axis. It
        // states the span actually plotted rather than a hard-coded window: a month scope draws that
        // month's days, and only all time falls back to the trailing thirty.
        subtitle = pluralStringResource(
            R.plurals.chart_daily_hours_subtitle,
            uiState.dailySeries.size,
            uiState.dailySeries.size,
        ),
    ) {
        LineChart(
            values = hours,
            referenceValue = average,
            lineColor = SparkTheme.colors.main,
            fillColor = SparkTheme.colors.main.copy(alpha = 0.25f),
            referenceColor = SparkTheme.colors.outline,
            // States the window and nothing else. No superlative here: naming the window's longest
            // day ranks one of the user's days against the others, which nothing in the app does.
            contentDescription = pluralStringResource(
                R.plurals.cd_daily_hours_chart,
                uiState.dailySeries.size,
                uiState.dailySeries.size,
            ),
            modifier = Modifier.fillMaxWidth().height(140.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(uiState.dailySeries.firstOrNull()?.date?.let { TimeFormat.axisDay(it, patterns) }.orEmpty())
            AxisLabel(uiState.dailySeries.lastOrNull()?.date?.let { TimeFormat.axisDay(it, patterns) }.orEmpty())
        }
    }
}

@Composable
internal fun SplitCard(uiState: ReportsUiState) {
    val showedUp = dayColor()
    // `outline`, which clears 3:1 on this card's raised paper in both themes, so the missed arc and its
    // legend dot are seen. Neutral, never red; a missed day is a fact, not a failure.
    val missed = SparkTheme.colors.outline

    ChartCard(title = stringResource(R.string.chart_split_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                values = listOf(uiState.showedUpDays.toFloat(), uiState.missedDays.toFloat()),
                colors = listOf(showedUp, missed),
                // Two counts, so neither can be the plural's own quantity: each is worded through
                // `days_count` first and arrives here as a phrase.
                contentDescription = stringResource(
                    R.string.cd_days_split,
                    pluralStringResource(R.plurals.days_count, uiState.showedUpDays, uiState.showedUpDays),
                    pluralStringResource(R.plurals.days_count, uiState.missedDays, uiState.missedDays),
                ),
                // Nothing tracked yet still has to read as a ring rather than as blank space.
                emptyColor = missed,
                // The hole is left empty: the stats table directly above states `totalDays`, and a
                // chart repeating a number rather than showing a shape adds nothing.
                modifier = Modifier.size(DonutChartDefaults.size()),
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendRow(showedUp, stringResource(R.string.stat_showed_up), uiState.showedUpDays)
                LegendRow(missed, stringResource(R.string.stat_missed), uiState.missedDays)
            }
        }
    }
}

/**
 * Worked time per calendar month across the whole record — all time only.
 *
 * **The per-bar value labels give way before the bars do.** `ChartGeometry.barRects` divides the
 * width by the count, so a multi-year record leaves each bar a few dp and no room at all for a
 * figure underneath it; past [MONTH_LABELS_UP_TO] the row of totals is replaced by the first and
 * last month, the way the line chart labels its axis. Nothing is lost to a screen reader either
 * way — the `contentDescription` states every month and its total regardless.
 */
@Composable
internal fun MonthlyHoursCard(uiState: ReportsUiState) {
    val patterns = LocalTimePatterns.current
    val hours = uiState.monthlySeries.map { it.workedMs / MILLIS_PER_HOUR }
    // Asked once for the series rather than per label, so every bar in one chart is formatted alike.
    val multiYear = uiState.monthlySeries.map { it.month.year }.distinct().size > 1
    val label = { point: MonthPoint -> TimeFormat.axisMonth(point.month, multiYear, patterns) }
    val entry = stringResource(R.string.cd_monthly_chart_entry)
    val spoken = uiState.monthlySeries.joinToString(stringResource(R.string.list_separator)) {
        val duration = TimeFormat.durationShort(it.workedMs, patterns)
        String.format(Locale.getDefault().withLatinDigits(), entry, label(it), duration)
    }

    ChartCard(title = stringResource(R.string.chart_monthly_title)) {
        BarChart(
            values = hours,
            barColor = SparkTheme.colors.main,
            baselineColor = SparkTheme.colors.outline,
            contentDescription = stringResource(R.string.cd_monthly_chart, spoken),
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (uiState.monthlySeries.size <= MONTH_LABELS_UP_TO) {
            // Bars alone carry no scale, so at this width each one states its own total underneath.
            Row(modifier = Modifier.fillMaxWidth()) {
                uiState.monthlySeries.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AxisLabel(label(point))
                        Text(
                            text = TimeFormat.durationShort(point.workedMs, patterns),
                            style = SparkTheme.typography.caption.highlight,
                        )
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AxisLabel(uiState.monthlySeries.firstOrNull()?.let(label).orEmpty())
                AxisLabel(uiState.monthlySeries.lastOrNull()?.let(label).orEmpty())
            }
        }
    }
}

/** Above this many bars there is no room for a total under each, and the axis takes over. */
private const val MONTH_LABELS_UP_TO = 6

@Composable
internal fun StartTimesCard(uiState: ReportsUiState) {
    val morning = uiState.startBuckets[StartBucket.MORNING] ?: 0
    val afternoon = uiState.startBuckets[StartBucket.AFTERNOON] ?: 0
    val evening = uiState.startBuckets[StartBucket.EVENING] ?: 0

    ChartCard(title = stringResource(R.string.chart_start_times_title)) {
        SplitRow(
            values = listOf(morning, afternoon, evening),
            colors = descriptiveHues(),
            labels = listOf(
                stringResource(R.string.bucket_morning),
                stringResource(R.string.bucket_afternoon),
                stringResource(R.string.bucket_evening),
            ),
            // Three counts, so none can be the plural's own quantity: each is worded through
            // `sessions_count` first and the sentence takes them as `%s`.
            contentDescription = stringResource(
                R.string.cd_start_time_split,
                sessionsPhrase(morning),
                sessionsPhrase(afternoon),
                sessionsPhrase(evening),
            ),
        )
    }
}

/**
 * How many blocks a day is broken into.
 *
 * A day of one long session and a day of four short ones are two rhythms, not a better and a worse
 * one, so the slices are the same three neutral hues and nothing here is highlighted. The mean sits
 * in the stats table above rather than in the hole, where it would read as a figure the ring measures.
 */
@Composable
internal fun SessionsPerDayCard(uiState: ReportsUiState) {
    val one = uiState.sessionBands[SessionBand.ONE] ?: 0
    val two = uiState.sessionBands[SessionBand.TWO] ?: 0
    val threePlus = uiState.sessionBands[SessionBand.THREE_PLUS] ?: 0
    val average = formatAverage(uiState.avgSessionsPerDay)

    ChartCard(title = stringResource(R.string.chart_sessions_per_day_title)) {
        SplitRow(
            values = listOf(one, two, threePlus),
            colors = descriptiveHues(),
            labels = listOf(
                pluralStringResource(R.plurals.sessions_count, 1, 1),
                pluralStringResource(R.plurals.sessions_count, 2, 2),
                stringResource(R.string.band_three_plus),
            ),
            contentDescription = stringResource(
                R.string.cd_sessions_per_day_split,
                daysPhrase(one),
                daysPhrase(two),
                daysPhrase(threePlus),
                average,
            ),
        )
    }
}

/**
 * A donut over [values] with a legend beside it — the shape both descriptive splits share.
 *
 * The colours are handed in rather than derived from the values, because deriving them is how a
 * ramp gets back in: any mapping from a magnitude to a strength ranks the slices.
 */
@Composable
private fun SplitRow(values: List<Int>, colors: List<Color>, labels: List<String>, contentDescription: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DonutChart(
            values = values.map { it.toFloat() },
            colors = colors,
            contentDescription = contentDescription,
            // Nothing recorded yet still has to read as a ring rather than as blank space.
            emptyColor = SparkTheme.colors.outline,
            modifier = Modifier.size(DonutChartDefaults.size()),
        )
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, label ->
                LegendRow(colors[index], label, values[index])
            }
        }
    }
}

/** One decimal place: the figure is a rhythm, and a second digit implies a precision it lacks. */
private fun formatAverage(value: Float): String = String.format(Locale.getDefault().withLatinDigits(), "%.1f", value)

@Composable
private fun sessionsPhrase(count: Int): String = pluralStringResource(R.plurals.sessions_count, count, count)

@Composable
private fun daysPhrase(count: Int): String = pluralStringResource(R.plurals.days_count, count, count)
