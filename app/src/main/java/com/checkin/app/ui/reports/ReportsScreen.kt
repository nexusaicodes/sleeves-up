package com.checkin.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adevinta.spark.components.buttons.Button
import com.adevinta.spark.components.buttons.Primary
import com.adevinta.spark.components.segmentedcontrol.SegmentedControl
import com.adevinta.spark.icons.DownloadFileOutline
import com.adevinta.spark.icons.GraphOutline
import com.adevinta.spark.icons.LeboncoinIcons
import com.checkin.app.R
import com.checkin.app.platform.ExportResult
import com.checkin.app.ui.components.ChartCard
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.LocalSnackbarHostState
import com.checkin.app.ui.components.MonthStepper
import com.checkin.app.ui.theme.LocalTimePatterns
import com.checkin.app.util.TimeFormat

/**
 * The Reports tab: every figure, chart and the CSV export read the one window that
 * [resolve] produces from the selected scope.
 *
 * **Width is capped in `NavigationGraph`**, which wraps this screen in `ConstrainedContent` — the
 * cap is not set here, so a large-screen layout question is answered one file over.
 */
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
    // three `stringResource` values, one of which cannot be resolved ahead of the event.
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
                hasNextMonth = uiState.hasNextMonth,
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() },
                onSelectMonth = { viewModel.selectMonth() },
                onSelectAllTime = { viewModel.selectAllTime() },
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
 * Two scope kinds, so each card has one rule per kind rather than a matrix of them: a segmented
 * Month / All time choice, and under it a month stepper. Stepping back is **unbounded** — a month
 * before the record began is an empty state rather than a disabled arrow, because a disabled control
 * says "not allowed" where the honest answer is "nothing here". Stepping forward stops at the month in
 * progress ([ReportsUiState.hasNextMonth]): a month that has not begun is not a record to read.
 */
@Composable
private fun ScopeSelector(
    scope: ReportScope,
    hasNextMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectMonth: () -> Unit,
    onSelectAllTime: () -> Unit,
) {
    val allTime = scope is ReportScope.AllTime

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SegmentedControl.Horizontal(
            selectedIndex = if (allTime) 1 else 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            singleLine(text = stringResource(R.string.scope_month), selected = !allTime, onClick = onSelectMonth)
            singleLine(text = stringResource(R.string.scope_all_time), selected = allTime, onClick = onSelectAllTime)
        }
        // The steppers hold their slot under all time rather than vanishing, so the row does not
        // reflow around the choice every time it changes.
        MonthStepper(
            label = scopeLabel(scope),
            previousEnabled = !allTime,
            nextEnabled = hasNextMonth,
            onPrevious = onPrevious,
            onNext = onNext,
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
    is ReportScope.Month -> TimeFormat.monthYear(scope.month, LocalTimePatterns.current)
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
        icon = LeboncoinIcons.GraphOutline,
        title = stringResource(R.string.empty_scope_title),
        // The month copy sends the user to the arrows; under all time those arrows are disabled, so
        // it says what would actually fill the scope instead of naming a control that cannot move.
        message = when (scope) {
            is ReportScope.Month ->
                stringResource(R.string.empty_scope_message, scopeLabel(scope, inline = true))
            ReportScope.AllTime -> stringResource(R.string.empty_scope_message_all_time)
        },
    )
}

/**
 * Exports what the screen is showing.
 *
 * One button that follows the scope selector rather than a range choice of its own: a second answer
 * to the selector's question is a control that can disagree with the screen above it. Any past month
 * is exportable by stepping to it.
 */
@Composable
private fun ExportCard(scope: ReportScope, onExport: () -> Unit) {
    ChartCard(title = stringResource(R.string.export_title)) {
        // Spark draws a button's icon with no description: the label conveys the action.
        Button.Primary(
            onClick = onExport,
            text = stringResource(R.string.export_scope, scopeLabel(scope, inline = true)),
            modifier = Modifier.fillMaxWidth(),
            icon = LeboncoinIcons.DownloadFileOutline,
        )
    }
}
