package com.checkin.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.checkin.app.R
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.dayColor
import com.checkin.app.util.TimeFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month of cells, each either a day the user showed up or a day they did not.
 *
 * **The shade is binary and must stay that way.** A cell used to be drawn at a strength proportional
 * to its hours against the user's longest day; that made every ordinary day render as a partial
 * version of their best one, and a single nine-hour day re-graded the whole history behind it. A
 * 45-minute day and a nine-hour day are now the same mark, which is what "a day counts because it
 * has a session" looks like on a calendar.
 *
 * @param trackingStartDate the day of the first session, or null when there are none yet — in which
 *   case no day is inside the tracked window, so the whole month draws as days the record does not
 *   cover rather than as days that were missed.
 * @param today the day that carries the marker, and the first day of the future — separate from
 *   [countedThrough], because today is drawn as today whether or not it has been checked out of yet.
 * @param countedThrough the last day that shades. Today once it holds a completed session, otherwise
 *   yesterday, so a day takes its colour at check-out rather than at the next midnight.
 * @param cellHeight height of a single day cell. The caller derives it from the viewport so the grid
 *   can claim the top half of the screen instead of sitting in a fixed 48dp band.
 */
@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    summaries: Map<String, DailyAggregate>,
    selectedDateKey: String?,
    trackingStartDate: LocalDate?,
    today: LocalDate,
    countedThrough: LocalDate,
    onDayClick: (String) -> Unit,
    cellHeight: Dp = 48.dp,
) {
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val startOffset = startOffsetIn(yearMonth)
    val weekDays = (0L..6L).map { firstDayOfWeek.plus(it) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val totalDays = yearMonth.lengthOfMonth()
        val rows = weekRowsIn(yearMonth)

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startOffset + 1

                    if (dayNum in 1..totalDays) {
                        val date = yearMonth.atDay(dayNum)
                        val key = date.format(dateFormatter)
                        val summary = summaries[key]
                        val isSelected = key == selectedDateKey
                        val isToday = date == today
                        // Today shades like any other recorded day once it has been checked out of,
                        // keeping its marker on top; while it is still in progress it carries the
                        // marker alone, since it is not counted anywhere else either.
                        val isTracked = trackingStartDate != null &&
                            !date.isBefore(trackingStartDate) &&
                            !date.isAfter(countedThrough)
                        // Days the record does not cover: not yet lived, before the user began, or
                        // every day at once while there is nothing recorded to begin from.
                        val isOutsideWindow = trackingStartDate == null ||
                            date.isBefore(trackingStartDate) ||
                            date.isAfter(today)

                        DayCell(
                            day = dayNum,
                            summary = summary.takeIf { isTracked },
                            isSelected = isSelected,
                            isToday = isToday,
                            isOutsideWindow = isOutsideWindow,
                            modifier = Modifier.weight(1f),
                            cellHeight = cellHeight,
                            onClick = { onDayClick(key) },
                        )
                    } else {
                        // Leading/trailing blank, kept at cell height so the row aligns.
                        Box(modifier = Modifier.weight(1f).heightIn(min = cellHeight))
                    }
                }
            }
        }
    }
}

/**
 * The one weight a recorded day is drawn at. A full-strength fill would win against the day number
 * sitting on it, so the hue stays a tint rather than a block.
 *
 * A constant, not a figure derived from the day's hours: every day the user showed up is drawn
 * identically, whatever it held.
 */
private const val RECORDED_DAY_ALPHA = 0.35f

/**
 * How far a day outside the tracked window is faded back.
 *
 * **Only the future and the days before tracking began are faded — never a day the user missed.** The
 * fade separates "no record kept" from "in the record"; applied to the past it would land on exactly
 * the empty days the record covers, and a day drawn fainter for holding nothing is a verdict, which
 * this calendar never renders. It is allowed to be colour-only because a date after today already
 * reads as the future from the number itself, and a date before the record began sits outside every
 * count the screen states.
 */
private const val OUTSIDE_WINDOW_ALPHA = 0.38f

@Composable
private fun DayCell(
    day: Int,
    summary: DailyAggregate?,
    isSelected: Boolean,
    isToday: Boolean,
    isOutsideWindow: Boolean,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 48.dp,
    onClick: () -> Unit,
) {
    // A day with no sessions gets no shade at all: an empty cell, not a coloured failure.
    val dayShade = dayColor().copy(alpha = RECORDED_DAY_ALPHA)

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        summary != null -> dayShade
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        isOutsideWindow -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OUTSIDE_WINDOW_ALPHA)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // The shade says only that the day has a session, so the hours are stated here rather than left
    // to a mark that does not carry them.
    val cellDescription = summary?.let {
        stringResource(R.string.cd_day_worked, day, TimeFormat.durationShort(it.totalDurationMs))
    } ?: day.toString()

    Box(
        modifier = modifier
            .heightIn(min = cellHeight)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cellDescription },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics { }, // parent's contentDescription conveys the cell
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarGridPreview() {
    CheckInAppTheme {
        val month = YearMonth.of(2026, 6)
        val summaries = mapOf(
            "2026-06-02" to DailyAggregate("2026-06-02", 8 * 3_600_000L, 1, 0L, 0L, 0, 1, 0, 0),
            "2026-06-04" to DailyAggregate("2026-06-04", 4 * 3_600_000L, 1, 0L, 0L, 0, 1, 0, 0),
            "2026-06-05" to DailyAggregate("2026-06-05", 45 * 60_000L, 1, 0L, 0L, 0, 1, 0, 0),
        )
        CalendarGrid(
            yearMonth = month,
            summaries = summaries,
            selectedDateKey = "2026-06-04",
            trackingStartDate = month.atDay(1),
            today = month.atDay(15),
            countedThrough = month.atDay(14),
            onDayClick = {},
        )
    }
}

private const val DAYS_PER_WEEK = 7

/**
 * The column [month]'s first day falls into, under the device locale's week start.
 *
 * Shared with `HistoryScreen`, which needs the row count below to size a cell *before* this grid is
 * composed. Two derivations of the same geometry is how the height gets computed for a different
 * number of rows than the grid then draws.
 */
internal fun startOffsetIn(month: YearMonth): Int {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
}

/** Week rows [month] occupies — 4 to 6. A partial trailing week still takes a row, so this rounds up. */
internal fun weekRowsIn(month: YearMonth): Int =
    (startOffsetIn(month) + month.lengthOfMonth() + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
