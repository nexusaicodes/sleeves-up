package com.checkin.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.adevinta.spark.SparkTheme
import com.adevinta.spark.components.text.Text
import com.checkin.app.R
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.theme.CheckInAppTheme
import com.checkin.app.ui.theme.LocalTimePatterns
import com.checkin.app.ui.theme.dayColor
import com.checkin.app.util.MILLIS_PER_HOUR
import com.checkin.app.util.MILLIS_PER_MINUTE
import com.checkin.app.util.TimeFormat
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinYearMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month of cells, each either a day the user showed up or a day they did not.
 *
 * **The shade is binary and must stay that way.** A strength proportional to a day's hours against
 * the user's longest day renders every ordinary day as a partial version of their best one, and a
 * single nine-hour day re-grades the whole history behind it (see `dayColor`). A 45-minute day and a
 * nine-hour day are the same mark, which is what "a day counts because it has a session" looks like
 * on a calendar.
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
    val locale = LocalTimePatterns.current.locale
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val weekStart = firstDayOfWeek.toKotlinWeekStart()
    val startOffset = CalendarMonth.leadingBlanks(yearMonth.toKotlinYearMonth(), weekStart)
    val weekDays = (0L..6L).map { firstDayOfWeek.plus(it) }
    val days = CalendarMonth.days(
        month = yearMonth.toKotlinYearMonth(),
        recordedKeys = summaries.keys,
        trackingStart = trackingStartDate?.toKotlinLocalDate(),
        today = today.toKotlinLocalDate(),
        countedThrough = countedThrough.toKotlinLocalDate(),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = SparkTheme.typography.small,
                    color = SparkTheme.colors.onBackgroundVariant,
                )
            }
        }

        val rows = CalendarMonth.weekRows(yearMonth.toKotlinYearMonth(), weekStart)

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (col in 0 until CalendarMonth.DAYS_PER_WEEK) {
                    val day = days.getOrNull(row * CalendarMonth.DAYS_PER_WEEK + col - startOffset)
                    if (day != null) {
                        DayCell(
                            day = day.date.dayOfMonth,
                            summary = summaries[day.dateKey].takeIf { day.recorded },
                            isSelected = day.dateKey == selectedDateKey,
                            isToday = day.today,
                            isOutsideWindow = day.outsideRecord,
                            modifier = Modifier.weight(1f),
                            cellHeight = cellHeight,
                            onClick = { onDayClick(day.dateKey) },
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
private const val RECORDED_DAY_ALPHA = 0.40f

/** The selected day's ring: ink, 7:1 on a recorded cell and 11:1 on the page. */
private val SELECTION_RING = 2.dp

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
    val patterns = LocalTimePatterns.current
    // A day with no sessions gets no shade at all: an empty cell, not a coloured failure.
    val dayShade = dayColor().copy(alpha = RECORDED_DAY_ALPHA)

    val bgColor = if (summary != null) dayShade else Color.Transparent

    // The selection is a ring over the cell's own state rather than a fill replacing it, so a
    // selected day still shows whether it was recorded, and no fill can be mistaken for a recorded
    // day's brass.
    val cellShape = SparkTheme.shapes.small
    val selection = if (isSelected) {
        Modifier.border(SELECTION_RING, SparkTheme.colors.onSurface, cellShape)
    } else {
        Modifier
    }

    val textColor = when {
        isToday -> SparkTheme.colors.main
        // Only the future and the days before tracking began are faded, to Spark's disabled emphasis,
        // and never a day the user missed. The fade separates "no record kept" from "in the record";
        // applied to the past it would land on exactly the empty days the record covers, and a day
        // drawn fainter for holding nothing is a verdict, which this calendar never renders. Colour
        // alone is enough: a date after today reads as the future from its number, and a date before
        // the record began sits outside every count the screen states.
        isOutsideWindow -> SparkTheme.colors.onBackgroundVariant.copy(alpha = SparkTheme.colors.dim3)
        else -> SparkTheme.colors.onSurface
    }

    // The shade says only that the day has a session, so the hours are stated here rather than left
    // to a mark that does not carry them.
    val cellDescription = summary?.let {
        stringResource(R.string.cd_day_worked, day, TimeFormat.durationShort(it.totalDurationMs, patterns))
    } ?: day.toString()

    Box(
        modifier = modifier
            .heightIn(min = cellHeight)
            .padding(2.dp)
            .clip(cellShape)
            .background(bgColor)
            .then(selection)
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
                style = SparkTheme.typography.body2,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(SparkTheme.colors.main, SparkTheme.shapes.full)
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
            "2026-06-02" to DailyAggregate("2026-06-02", 8 * MILLIS_PER_HOUR, 1, 0L, 0L, 0, 1, 0, 0, 0),
            "2026-06-04" to DailyAggregate("2026-06-04", 4 * MILLIS_PER_HOUR, 1, 0L, 0L, 0, 1, 0, 0, 0),
            "2026-06-05" to DailyAggregate("2026-06-05", 45 * MILLIS_PER_MINUTE, 1, 0L, 0L, 0, 1, 0, 0, 0),
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

/** [CalendarMonth]'s week start, from the locale's. */
private fun DayOfWeek.toKotlinWeekStart(): kotlinx.datetime.DayOfWeek = kotlinx.datetime.DayOfWeek(value)

/**
 * Week rows [month] occupies under [locale]'s week start. `HistoryScreen` sizes a cell from it before
 * this grid is composed; both read [CalendarMonth.weekRows], so they count the same rows.
 */
internal fun weekRowsIn(month: YearMonth, locale: Locale): Int =
    CalendarMonth.weekRows(month.toKotlinYearMonth(), WeekFields.of(locale).firstDayOfWeek.toKotlinWeekStart())
