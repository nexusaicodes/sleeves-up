package com.checkin.app.ui.history

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.checkin.app.R
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.ui.components.ConstrainedContent
import com.checkin.app.ui.components.EmptyState
import com.checkin.app.ui.components.SessionIntervalRow
import com.checkin.app.util.TimeFormat
import java.time.YearMonth

/**
 * The History tab: a calendar, and the sessions of whichever day you tap. It derives nothing — no
 * summary, no average, no ratio; that work belongs to Reports, and two tabs computing the same class
 * of figure at two scopes is how they come to disagree.
 *
 * **Width is not capped here, and that is decided in `NavigationGraph`** — this screen manages its
 * own, going two-pane on expanded widths, so it is the one destination the graph does not wrap in
 * `ConstrainedContent`.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun HistoryScreen(
    innerPadding: PaddingValues,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Roll the date window forward when the screen resumes. Tracking start needs no re-read: it is
    // a Room flow and stays subscribed, which is exactly what `dayTrigger`'s dedupe preserves.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val widthSizeClass = calculateWindowSizeClass(LocalContext.current as Activity).widthSizeClass
    val topPad = innerPadding.calculateTopPadding() + 16.dp
    val bottomPad = innerPadding.calculateBottomPadding() + 8.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Cells are sized from what the grid can actually have: the real viewport (not the physical
        // screen, which counts the app bar and bottom nav as usable) less the chrome, the month
        // selector, the weekday header and — on a phone — the day slot below the grid.
        val available = maxHeight - topPad - bottomPad
        // On an expanded width the day slot sits in its own pane beside the calendar, so it costs
        // the grid no height at all; only the single-column layout has to reserve room under it.
        val slotBudget = if (widthSizeClass == WindowWidthSizeClass.Expanded) {
            0.dp
        } else {
            val textGrowth = TEXT_CONTENT_HEIGHT * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
            DETAIL_SLOT_HEIGHT + SECTION_SPACING + textGrowth
        }
        val gridBudget = available - MONTH_SELECTOR_HEIGHT - WEEKDAY_HEADER_HEIGHT -
            SECTION_SPACING - slotBudget
        // The floor wins over fitting: 48dp is the minimum tap target, so a 6-row month on a small
        // screen scrolls a little rather than shrinking its days below it.
        val cellHeight = (gridBudget / weekRowsIn(uiState.currentMonth))
            .coerceIn(MIN_CELL_HEIGHT, MAX_CELL_HEIGHT)

        HistoryContent(
            uiState = uiState,
            viewModel = viewModel,
            widthSizeClass = widthSizeClass,
            cellHeight = cellHeight,
            topPad = topPad,
            bottomPad = bottomPad,
        )
    }
}

@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    viewModel: HistoryViewModel,
    widthSizeClass: WindowWidthSizeClass,
    cellHeight: Dp,
    topPad: Dp,
    bottomPad: Dp,
) {
    if (widthSizeClass == WindowWidthSizeClass.Expanded) {
        // Two-pane: the calendar on the left, the selected day's sessions on the right.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                calendarItems(uiState, viewModel, cellHeight)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                daySlotItems(uiState)
            }
        }
    } else {
        ConstrainedContent {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                calendarItems(uiState, viewModel, cellHeight)
                daySlotItems(uiState)
            }
        }
    }
}

private fun LazyListScope.calendarItems(uiState: HistoryUiState, viewModel: HistoryViewModel, cellHeight: Dp) {
    item {
        MonthSelector(
            currentMonth = uiState.currentMonth,
            onPrevious = { viewModel.previousMonth() },
            onNext = { viewModel.nextMonth() },
        )
    }
    item {
        CalendarGrid(
            yearMonth = uiState.currentMonth,
            summaries = uiState.summaries,
            selectedDateKey = uiState.selectedDateKey,
            trackingStartDate = uiState.trackingStartDate,
            today = uiState.today,
            countedThrough = uiState.countedThrough,
            onDayClick = { viewModel.selectDay(it) },
            cellHeight = cellHeight,
        )
    }
}

private val MIN_CELL_HEIGHT = 48.dp

// A cell is only ~53dp wide on a phone, so an unbounded height stretches it into a ribbon. This cap
// keeps the proportions sane and leaves the day slot on screen alongside the grid.
private val MAX_CELL_HEIGHT = 80.dp

// What the grid has to share the viewport with, measured from the composables themselves.
private val MONTH_SELECTOR_HEIGHT = 48.dp
private val WEEKDAY_HEADER_HEIGHT = 20.dp

/**
 * The day-detail slot, sized to whichever of its states is tallest.
 *
 * That is the placeholder rather than the sessions: `EmptyState` is 24dp of padding at each end, a
 * 48dp icon, 12dp, a title line, 4dp and a two-line message — around 176dp, against roughly 140dp
 * for a date, a summary line and three ledger rows.
 *
 * It is a constant rather than a measurement because the grid above has to be sized before the slot
 * below it is laid out. **Keep it in step with [daySlotItems]** — over-stating it costs the grid height
 * it could have used, and under-stating it pushes the slot off the bottom of the viewport.
 */
private val DETAIL_SLOT_HEIGHT = 180.dp
private val SECTION_SPACING = 16.dp

/**
 * The part of the above that grows with the user's font setting: the slot is text all the way down
 * now — a title, a two-line message, or a date, a summary line and the ledger rows.
 *
 * Deliberately generous, and the two errors are not symmetric: over-stating only shrinks the grid,
 * which the 48dp cell floor absorbs by scrolling, whereas under-stating clips the slot.
 */
private val TEXT_CONTENT_HEIGHT = 110.dp

/**
 * The selected day, as the rows the record actually holds.
 *
 * Three states, and the middle one is the reason this is a slot rather than a conditional: every day
 * in the grid is tappable, including one with nothing on it and one in the future. Falling back to
 * something else there — a summary, or the previous day's rows — makes the tap read as though it
 * did nothing at all.
 *
 * There is no aggregation here beyond the day's own total, which is the sum of the rows printed
 * directly beneath it. History states what was recorded; the deriving belongs on Reports.
 */
private fun LazyListScope.daySlotItems(uiState: HistoryUiState) {
    val dateKey = uiState.selectedDateKey
    val sessions = uiState.selectedDaySessions

    when {
        dateKey == null -> item {
            EmptyState(
                icon = Icons.Default.Event,
                title = stringResource(R.string.empty_day_detail_title),
                message = stringResource(R.string.empty_day_detail_message),
            )
        }

        sessions.isEmpty() -> item {
            EmptyState(
                icon = Icons.Default.Event,
                // Names the day rather than restating "no day selected" — the tap did land.
                title = TimeFormat.dateKeyWithWeekday(dateKey).orEmpty(),
                message = stringResource(R.string.empty_day_sessions_message),
            )
        }

        // Header and rows are **one** item, not one per session: the enclosing LazyColumn spaces
        // its items for cards, and a ledger set at that pitch reads as a list of unrelated lines
        // rather than as the shape of a day. The rows carry their own 4dp, as they do on Check-In.
        else -> item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DayHeader(dateKey, sessions)
                // The open session is wanted: History would otherwise render today as incomplete
                // against the Check-In screen, with nothing on the tab to explain the difference.
                sessions.forEach { session -> SessionIntervalRow(session) }
            }
        }
    }
}

/**
 * The day, and what it held.
 *
 * The total sums only the completed intervals — an open one carries no `duration` — which is the
 * same figure the rows below print, added up. Nothing is measured against it.
 */
@Composable
private fun DayHeader(dateKey: String, sessions: List<CheckInSession>) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        TimeFormat.dateKeyWithWeekday(dateKey)?.let { date ->
            Text(
                text = date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(
                R.string.day_sessions_summary,
                pluralStringResource(R.plurals.sessions_count, sessions.size, sessions.size),
                TimeFormat.durationShort(sessions.sumOf { it.duration ?: 0L }),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthSelector(currentMonth: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month),
            )
        }
        Text(
            text = TimeFormat.monthYear(currentMonth),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month),
            )
        }
    }
}
