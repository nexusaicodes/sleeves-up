package com.checkin.app.ui.reports

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.reports.DAILY_WINDOW_DAYS
import com.checkin.app.ui.reports.DayPoint
import com.checkin.app.ui.reports.MonthPoint
import com.checkin.app.ui.reports.ReportScope
import com.checkin.app.ui.reports.ReportWindow
import com.checkin.app.ui.reports.dailySeries
import com.checkin.app.ui.reports.monthlySeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The two chart series, read straight off a window's summaries: gap-filled, and capped under all time. */
class ReportsSeriesTest {

    private fun day(key: String, ms: Long) = DailyAggregate(
        dateKey = key,
        totalDurationMs = ms,
        sessionCount = 1,
        firstCheckIn = 0L,
        lastCheckOut = 0L,
        dayBoundaryCheckOuts = 0,
        inAppCheckOuts = 1,
        timerNotificationCheckOuts = 0,
        reminderNotificationCheckOuts = 0,
        widgetCheckOuts = 0,
    )

    private fun summaries(vararg days: DailyAggregate) = days.associateBy { it.dateKey }

    @Test
    fun `a month plots every day in its window, a day with no sessions as zero`() {
        val window = ReportWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3))

        val points =
            dailySeries(summaries(day("2026-06-02", 60_000L)), ReportScope.Month(YearMonth.of(2026, 6)), window)

        assertEquals(
            listOf(
                DayPoint(LocalDate.of(2026, 6, 1), 0L),
                DayPoint(LocalDate.of(2026, 6, 2), 60_000L),
                DayPoint(LocalDate.of(2026, 6, 3), 0L),
            ),
            points,
        )
    }

    @Test
    fun `all time plots only the trailing window of days`() {
        val window = ReportWindow(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 6, 30))

        val points = dailySeries(emptyMap(), ReportScope.AllTime, window)

        assertEquals(DAILY_WINDOW_DAYS, points.size)
        assertEquals(LocalDate.of(2026, 6, 30), points.last().date)
        assertEquals(LocalDate.of(2026, 6, 1), points.first().date)
    }

    @Test
    fun `all time shorter than the trailing window plots the whole record`() {
        val window = ReportWindow(LocalDate.of(2026, 6, 28), LocalDate.of(2026, 6, 30))

        assertEquals(3, dailySeries(emptyMap(), ReportScope.AllTime, window).size)
    }

    @Test
    fun `months are totalled and gap-filled across the window, oldest first`() {
        val window = ReportWindow(LocalDate.of(2026, 4, 20), LocalDate.of(2026, 6, 2))
        val record = summaries(day("2026-04-21", 1_000L), day("2026-04-30", 2_000L), day("2026-06-01", 5_000L))

        assertEquals(
            listOf(
                MonthPoint(YearMonth.of(2026, 4), 3_000L),
                MonthPoint(YearMonth.of(2026, 5), 0L),
                MonthPoint(YearMonth.of(2026, 6), 5_000L),
            ),
            monthlySeries(record, ReportScope.AllTime, window),
        )
    }

    @Test
    fun `a month scope has no monthly series`() {
        val window = ReportWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        assertTrue(
            monthlySeries(summaries(day("2026-06-01", 1L)), ReportScope.Month(YearMonth.of(2026, 6)), window).isEmpty(),
        )
    }

    @Test
    fun `a malformed date key is dropped rather than thrown on`() {
        val window = ReportWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))

        val months = monthlySeries(summaries(day("not-a-date", 9L), day("2026-06-05", 4L)), ReportScope.AllTime, window)

        assertEquals(listOf(MonthPoint(YearMonth.of(2026, 6), 4L)), months)
    }
}
