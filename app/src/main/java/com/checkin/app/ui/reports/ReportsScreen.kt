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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.checkin.app.platform.ExportResult
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.charts.BarChart
import com.checkin.app.ui.components.charts.DonutChart
import com.checkin.app.ui.components.charts.DonutChartDefaults
import com.checkin.app.ui.components.charts.LineChart
import com.checkin.app.ui.theme.dayColor
import com.checkin.app.util.TimeFormat
import java.time.format.DateTimeFormatter
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
        when {
            // Nothing is asserted about the record until it has been read. The empty state and the
            // cards are both wrong answers while loading, so the screen shows neither.
            uiState.loading -> Unit

            uiState.totalDays == 0 -> item {
                EmptyState(
                    icon = Icons.Default.Insights,
                    title = stringResource(R.string.empty_reports_title),
                    message = stringResource(R.string.empty_reports_message),
                )
            }

            else -> {
                item { DailyHoursCard(uiState) }
                item { SplitCard(uiState) }
                item { MonthlyHoursCard(uiState) }
                item { StreakCard(uiState) }
            }
        }

        // Export stays last so it never competes with the data for attention.
        item { ExportCard(onExport = { viewModel.exportCsv(it) }) }
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
        // The dashed line needs naming somewhere; the subtitle keeps it out of the date axis.
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
            contentDescription = pluralStringResource(
                R.plurals.cd_daily_hours_chart,
                uiState.dailySeries.size,
                uiState.dailySeries.size,
                TimeFormat.durationShort(uiState.dailySeries.maxOfOrNull { it.workedMs } ?: 0L),
            ),
            modifier = Modifier.fillMaxWidth().height(140.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AxisLabel(uiState.dailySeries.firstOrNull()?.date?.format(dayLabelFormat) ?: "")
            AxisLabel(uiState.dailySeries.lastOrNull()?.date?.format(dayLabelFormat) ?: "")
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
                    R.string.cd_alltime_split,
                    pluralStringResource(R.plurals.days_count, uiState.showedUpDays, uiState.showedUpDays),
                    pluralStringResource(R.plurals.days_count, uiState.missedDays, uiState.missedDays),
                ),
                // Nothing tracked yet still has to read as a ring rather than as blank space.
                emptyColor = missed,
                modifier = Modifier.size(DonutChartDefaults.size()),
            ) {
                // DonutChart bounds this to the ring's clear middle; the caption wraps to fit it.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${uiState.totalDays}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.stat_days_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        // At the largest font scales the ring stops growing, so the caption gives
                        // way rather than being clipped mid-word.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendRow(showedUp, stringResource(R.string.stat_showed_up), uiState.showedUpDays)
                LegendRow(missed, stringResource(R.string.stat_missed), uiState.missedDays)
            }
        }
    }
}

@Composable
private fun MonthlyHoursCard(uiState: ReportsUiState) {
    val hours = uiState.monthlySeries.map { it.workedMs / MS_PER_HOUR }

    ChartCard(title = stringResource(R.string.chart_monthly_title)) {
        BarChart(
            values = hours,
            barColor = MaterialTheme.colorScheme.primary,
            baselineColor = MaterialTheme.colorScheme.outline,
            contentDescription = stringResource(
                R.string.cd_monthly_chart,
                uiState.monthlySeries.joinToString(", ") {
                    "${it.month.format(monthLabelFormat)} ${TimeFormat.durationShort(it.workedMs)}"
                },
            ),
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        Spacer(Modifier.height(8.dp))
        // Bars alone carry no scale, so each one states its own total underneath.
        Row(modifier = Modifier.fillMaxWidth()) {
            uiState.monthlySeries.forEach { point ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AxisLabel(point.month.format(monthLabelFormat))
                    Text(
                        text = TimeFormat.durationShort(point.workedMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakCard(uiState: ReportsUiState) {
    ChartCard(title = stringResource(R.string.overall_stats_title)) {
        // The card only renders with tracked days behind it, so the start is present — but it is
        // derived from the sessions, so the row is dropped rather than invented if it is ever null.
        uiState.trackingStartDate?.let { start ->
            StatsRow(stringResource(R.string.stat_tracking_since), TimeFormat.dateWithYear(start))
        }
        StatsRow(stringResource(R.string.stat_total_tracked_days), "${uiState.totalDays}")
        StatsRow(
            stringResource(R.string.stat_current_streak),
            pluralStringResource(R.plurals.days_count, uiState.currentStreak, uiState.currentStreak),
        )
        StatsRow(
            stringResource(R.string.stat_best_streak),
            pluralStringResource(R.plurals.days_count, uiState.bestStreak, uiState.bestStreak),
        )
    }
}

@Composable
private fun ExportCard(onExport: (ExportRange) -> Unit) {
    ChartCard(title = stringResource(R.string.export_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onExport(ExportRange.THIS_MONTH) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                // Icon is decorative — the button's text label conveys the action.
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.export_this_month))
            }
            OutlinedButton(
                onClick = { onExport(ExportRange.ALL_TIME) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.export_all_time))
            }
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

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val dayLabelFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val monthLabelFormat = DateTimeFormatter.ofPattern("MMM", Locale.US)
