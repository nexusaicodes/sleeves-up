package com.checkin.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.SessionBand
import com.checkin.app.data.StartBucket
import com.checkin.app.platform.ExportResult
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.SectionDivider
import com.checkin.app.ui.components.charts.BarChart
import com.checkin.app.ui.components.charts.DonutChart
import com.checkin.app.ui.components.charts.DonutChartDefaults
import com.checkin.app.ui.components.charts.LineChart
import com.checkin.app.ui.theme.dayColor
import com.checkin.app.util.TimeFormat
import java.time.format.TextStyle
import java.util.Locale

private const val MS_PER_HOUR = 3_600_000f

@Composable
fun ReportsScreen(
    innerPadding: PaddingValues,
    viewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    // Surface each export outcome once as an auto-dismissing snackbar. The event flow is
    // non-replaying, so a config-change re-collect can't re-show a past result.
    val snackbarHostState = LocalSnackbarHostState.current
    // The failure case interpolates a runtime message, so this reads resources rather than hoisting
    // three `stringResource` values, only one of which could be resolved ahead of the event.
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { event ->
            val message = when (event) {
                ExportResult.Success -> resources.getString(R.string.export_success)
                ExportResult.Nothing -> resources.getString(R.string.export_nothing)
                is ExportResult.Failure -> resources.getString(R.string.export_failed, event.message ?: "")
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The selector renders in **every** state, the empty one included. It is the only way back
        // out of a month that holds nothing, and a screen whose sole control disappears exactly when
        // the user has navigated somewhere uninteresting is a dead end.
        item {
            ScopeSelector(
                scope = uiState.scope,
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() },
                onToggleAllTime = {
                    if (uiState.scope is ReportScope.AllTime) viewModel.selectMonth() else viewModel.selectAllTime()
                },
            )
        }

        when {
            // Nothing is asserted about the record until it has been read. The empty state and the
            // cards are both wrong answers while loading, so the screen shows neither.
            uiState.loading -> Unit

            uiState.window == null -> item { EmptyScope(uiState.scope) }

            else -> {
                item { ScopeStatsCard(uiState) }
                item { SplitCard(uiState) }
                item { DailyHoursCard(uiState) }
                // Two bars or more, which under all time means a record spanning more than one
                // month. A single bar states nothing the total above it has not already stated,
                // whether it is a month scope (always one) or a record only a month old.
                if (uiState.monthlySeries.size > 1) item { MonthlyHoursCard(uiState) }
                item { StartTimesCard(uiState) }
                item { SessionsPerDayCard(uiState) }
                // Export stays last so it never competes with the data for attention, and it is
                // inside the branch: a scope with no counted day has no file to write, so offering
                // the button there is offering a no-op the snackbar then has to refuse.
                item { ExportCard(scope = uiState.scope, onExport = { viewModel.exportCsv() }) }
            }
        }
    }
}

/**
 * What the screen is describing, and the only control on it.
 *
 * A month stepper with an all-time end-stop: two scope kinds, so each card has one rule per kind
 * rather than a matrix of them. Stepping is **unbounded**, exactly as History's navigator is — a
 * month before the record began is an empty state rather than a disabled arrow, because a disabled
 * control says "not allowed" where the honest answer is "nothing here".
 */
@Composable
private fun ScopeSelector(
    scope: ReportScope,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleAllTime: () -> Unit,
) {
    val allTime = scope is ReportScope.AllTime

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The steppers hold their slot under all time rather than vanishing, so the row does not
        // reflow around the toggle every time it is pressed.
        IconButton(onClick = onPrevious, enabled = !allTime) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        Text(
            text = scopeLabel(scope),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // Two lines rather than one: at a large font scale a single line clipped "August 2026"
            // to "Augus…", and a truncated month is not a date the user can read.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = !allTime) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month),
            )
        }
        FilterChip(
            selected = allTime,
            onClick = onToggleAllTime,
            label = { Text(stringResource(R.string.scope_all_time)) },
        )
    }
}

/**
 * The scope as a heading — the selector's own label, and the subject of the empty state.
 *
 * [inline] asks for the form that belongs inside a sentence. Only all time differs: a month name is
 * a proper noun and reads correctly either way, whereas the Title Case "All time" lands a capital
 * mid-clause in "Export all time".
 */
@Composable
private fun scopeLabel(scope: ReportScope, inline: Boolean = false): String = when (scope) {
    is ReportScope.Month ->
        "${scope.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${scope.month.year}"
    ReportScope.AllTime ->
        stringResource(if (inline) R.string.scope_all_time_inline else R.string.scope_all_time)
}

/**
 * A scope holding no counted day.
 *
 * Three causes and one answer: nothing recorded anywhere, a month outside the record, or a record
 * whose only day is a today still in progress. Zeros would be the wrong rendering — they assert that
 * the user showed up on none of some number of days, which is a verdict on days the record does not
 * cover.
 */
@Composable
private fun EmptyScope(scope: ReportScope) {
    EmptyState(
        icon = Icons.Default.Insights,
        title = stringResource(R.string.empty_scope_title),
        message = stringResource(R.string.empty_scope_message, scopeLabel(scope, inline = true)),
    )
}

/**
 * Every scalar the scope produces, as one dense table.
 *
 * The figures used to be spread between two donut holes, two legends and a stats card four cards
 * apart, so `totalDays` and `showedUpDays` each rendered twice with nothing added the second time.
 * They are stated once, here, and the charts below carry only what a chart adds.
 *
 * **Every row is a quantity and none is a rank.** The two averages arrived from History's deleted
 * month card, and they are allowed here for the reason they were refused there: a row states a
 * figure, where the ring measured it against a denominator these do not have — the only one
 * available is the user's own all-time equivalent, which is a personal best under another name.
 */
@Composable
private fun ScopeStatsCard(uiState: ReportsUiState) {
    ChartCard(title = stringResource(R.string.scope_stats_title)) {
        // Only meaningful under all time; a month scope's own start is the month, which the selector
        // states directly above.
        if (uiState.scope is ReportScope.AllTime) {
            uiState.trackingStartDate?.let { start ->
                StatsRow(stringResource(R.string.stat_tracking_since), TimeFormat.dateWithYear(start))
            }
        }
        StatsRow(stringResource(R.string.stat_tracked_days), "${uiState.totalDays}")
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
        StatsRow(stringResource(R.string.stat_total_hours), TimeFormat.durationShort(uiState.totalWorkedMs))
        StatsRow(
            stringResource(R.string.stat_total_sessions),
            pluralStringResource(R.plurals.sessions_count, uiState.totalSessions, uiState.totalSessions),
        )
        StatsRow(stringResource(R.string.stat_avg_per_day), TimeFormat.durationShort(uiState.avgDailyMs))
        StatsRow(stringResource(R.string.stat_sessions_per_day), formatAverage(uiState.avgSessionsPerDay))
    }
}

@Composable
private fun DailyHoursCard(uiState: ReportsUiState) {
    val hours = uiState.dailySeries.map { it.workedMs / MS_PER_HOUR }
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
            lineColor = MaterialTheme.colorScheme.primary,
            fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            referenceColor = MaterialTheme.colorScheme.outline,
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
            AxisLabel(uiState.dailySeries.firstOrNull()?.date?.let(TimeFormat::axisDay).orEmpty())
            AxisLabel(uiState.dailySeries.lastOrNull()?.date?.let(TimeFormat::axisDay).orEmpty())
        }
    }
}

@Composable
private fun SplitCard(uiState: ReportsUiState) {
    val showedUp = dayColor()
    // `outline`, not `outlineVariant`: the latter *is* `surfaceVariant` in the dark scheme, which is
    // this card's own container, so the missed arc and its legend dot would be painted in the
    // background and be invisible. Neutral, never red; a missed day is a fact, not a failure.
    val missed = MaterialTheme.colorScheme.outline

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
                // The hole is left empty: it used to print `totalDays` over a "days tracked"
                // caption, which the stats table directly above now states, and a chart repeating a
                // number rather than showing a shape is the duplication this layout removed.
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
private fun MonthlyHoursCard(uiState: ReportsUiState) {
    val hours = uiState.monthlySeries.map { it.workedMs / MS_PER_HOUR }
    // Asked once for the series rather than per label, so every bar in one chart is formatted alike.
    val multiYear = uiState.monthlySeries.map { it.month.year }.distinct().size > 1
    val label = { point: MonthPoint -> TimeFormat.axisMonth(point.month, multiYear) }

    ChartCard(title = stringResource(R.string.chart_monthly_title)) {
        BarChart(
            values = hours,
            barColor = MaterialTheme.colorScheme.primary,
            baselineColor = MaterialTheme.colorScheme.outline,
            contentDescription = stringResource(
                R.string.cd_monthly_chart,
                uiState.monthlySeries.joinToString(", ") {
                    "${label(it)} ${TimeFormat.durationShort(it.workedMs)}"
                },
            ),
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
                            text = TimeFormat.durationShort(point.workedMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
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
private fun StartTimesCard(uiState: ReportsUiState) {
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
 * in the stats table above rather than in the hole, where it read as a figure the ring was measuring.
 */
@Composable
private fun SessionsPerDayCard(uiState: ReportsUiState) {
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
            emptyColor = MaterialTheme.colorScheme.outline,
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

/**
 * Three hues of equal visual weight for a descriptive split.
 *
 * Deliberately not the day hue at three strengths: one colour at varying alpha is an intensity ramp,
 * and a ramp says one end is more than the other. These are three different colours saying three
 * different things.
 */
@Composable
private fun descriptiveHues(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.secondary,
)

/** One decimal place: the figure is a rhythm, and a second digit implies a precision it lacks. */
private fun formatAverage(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun sessionsPhrase(count: Int): String = pluralStringResource(R.plurals.sessions_count, count, count)

@Composable
private fun daysPhrase(count: Int): String = pluralStringResource(R.plurals.days_count, count, count)

/**
 * Exports what the screen is showing.
 *
 * One button, not the old This month / All time pair: the scope selector already asks that question,
 * and a second answer to it beside the first is a control that can disagree with the screen above
 * it. It also makes an arbitrary past month exportable, which the pair could not express.
 */
@Composable
private fun ExportCard(scope: ReportScope, onExport: () -> Unit) {
    ChartCard(title = stringResource(R.string.export_title)) {
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            // Icon is decorative — the button's text label conveys the action.
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.export_scope, scopeLabel(scope, inline = true)))
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
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
private fun AxisLabel(text: String) {
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
private fun StatsRow(label: String, value: String) {
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
