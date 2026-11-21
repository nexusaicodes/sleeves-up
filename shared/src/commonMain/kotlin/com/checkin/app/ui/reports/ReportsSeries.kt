package com.checkin.app.ui.reports

import com.checkin.app.data.local.DailyAggregate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/*
 * What the two time-series charts plot — one point per day (a line), one per month (a bar) — and the
 * pure functions that build them from a window's summaries. Both series are gap-filled, so a day or
 * month with no sessions is a real zero: a hole in a line chart reads as missing data rather than as
 * an absence.
 */

/** How many trailing days the daily chart plots under all time; a month scope plots its whole month. */
const val DAILY_WINDOW_DAYS = 30

/** A day's worked time, for the daily-hours chart. */
data class DayPoint(val date: LocalDate, val workedMs: Long)

/** A month's worked time, for the monthly-totals chart. */
data class MonthPoint(val month: YearMonth, val workedMs: Long)

/**
 * The days plotted by the daily chart, gap-filled: a day without sessions is emitted as a real
 * zero, since a hole in a line reads as missing data rather than as an absence.
 *
 * A month scope plots the whole month; all time plots the trailing [DAILY_WINDOW_DAYS], because
 * a line with a point per day of a multi-year record is a solid block.
 */
fun dailySeries(
    summaries: Map<String, DailyAggregate>,
    activeScope: ReportScope,
    window: ReportWindow,
): List<DayPoint> {
    val from = when (activeScope) {
        is ReportScope.Month -> window.start
        ReportScope.AllTime -> maxOf(window.start, window.end.minus(DAILY_WINDOW_DAYS - 1, DateTimeUnit.DAY))
    }
    return generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= window.end }
        .map { day -> DayPoint(day, summaries[day.toString()]?.totalDurationMs ?: 0L) }
        .toList()
}

/**
 * Worked time per calendar month across the window, oldest first — **all time only**.
 *
 * A month scope spans one month, and a single bar states nothing a chart adds over the total
 * already printed above it, so the card is not rendered at all rather than rendered degenerate.
 */
fun monthlySeries(
    summaries: Map<String, DailyAggregate>,
    activeScope: ReportScope,
    window: ReportWindow,
): List<MonthPoint> {
    if (activeScope !is ReportScope.AllTime) return emptyList()
    // A malformed date_key is dropped rather than thrown on, matching how the repository and the
    // formatters read that column: one unparseable row must not strand the whole screen in
    // loading, on a table the app gives no way to edit.
    val totals = summaries.values.groupBy { monthOf(it.dateKey) }
        .filterKeys { it != null }
        .mapValues { (_, days) -> days.sumOf { it.totalDurationMs } }
    val lastMonth = window.end.yearMonth
    return generateSequence(window.start.yearMonth) { it.firstDay.plus(1, DateTimeUnit.MONTH).yearMonth }
        .takeWhile { it <= lastMonth }
        .map { month -> MonthPoint(month, totals[month] ?: 0L) }
        .toList()
}

private fun monthOf(dateKey: String): YearMonth? = runCatching { LocalDate.parse(dateKey).yearMonth }.getOrNull()
