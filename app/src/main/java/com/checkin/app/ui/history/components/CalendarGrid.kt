package com.checkin.app.ui.history.components

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
 * @param peakDayMs the longest day on record, which every cell's strength is measured against. A day
 *   is drawn in one hue at a strength proportional to its hours — never as a verdict, and never red.
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
    peakDayMs: Long,
    onDayClick: (String) -> Unit,
    cellHeight: Dp = 48.dp,
) {
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val locale = Locale.getDefault()
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val firstDayOfMonth = yearMonth.atDay(1)
    val startOffset = (firstDayOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
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
        val totalCells = startOffset + totalDays
        val rows = (totalCells + 6) / 7

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
                            peakDayMs = peakDayMs,
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
 * How much of the day hue a cell's background may reach. A full-strength fill would win against the
 * day number sitting on it; this keeps the strongest day a tint rather than a block.
 */
private const val BACKGROUND_STRENGTH = 0.35f

/**
 * How far a day outside the tracked window is faded back.
 *
 * **Only the future and the days before tracking began are faded — never a day the user missed.** The
 * fade separates "no record kept" from "in the record"; applied to the past it would land on exactly
 * the empty days the record covers, and a day drawn fainter for holding nothing is a verdict, which
 * this calendar never renders. It is the one mark here allowed to be colour-only, because a date
 * after today already reads as the future from the number itself — unlike the intensity shade, which
 * encodes hours nothing else on the cell says.
 */
private const val OUTSIDE_WINDOW_ALPHA = 0.38f

@Composable
private fun DayCell(
    day: Int,
    summary: DailyAggregate?,
    peakDayMs: Long,
    isSelected: Boolean,
    isToday: Boolean,
    isOutsideWindow: Boolean,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 48.dp,
    onClick: () -> Unit,
) {
    // A day with no sessions gets no shade at all: an empty cell, not a coloured failure.
    val fraction = DayIntensity.fractionOf(summary?.totalDurationMs ?: 0L, peakDayMs)
    val dayShade = dayColor(fraction * BACKGROUND_STRENGTH)

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        fraction > 0f -> dayShade
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isToday -> MaterialTheme.colorScheme.primary
        isOutsideWindow -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OUTSIDE_WINDOW_ALPHA)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // The shade stands for a quantity, so a screen reader is given the quantity itself — colour is
    // never the only carrier.
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
            "2026-06-02" to DailyAggregate("2026-06-02", 8 * 3_600_000L, 1, 0L, 0L),
            "2026-06-04" to DailyAggregate("2026-06-04", 4 * 3_600_000L, 1, 0L, 0L),
            "2026-06-05" to DailyAggregate("2026-06-05", 45 * 60_000L, 1, 0L, 0L),
        )
        CalendarGrid(
            yearMonth = month,
            summaries = summaries,
            selectedDateKey = "2026-06-04",
            trackingStartDate = month.atDay(1),
            today = month.atDay(15),
            countedThrough = month.atDay(14),
            peakDayMs = 8 * 3_600_000L,
            onDayClick = {},
        )
    }
}
