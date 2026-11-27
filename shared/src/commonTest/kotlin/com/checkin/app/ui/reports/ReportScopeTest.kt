package com.checkin.app.ui.reports

import com.checkin.app.ui.reports.ReportScope
import com.checkin.app.ui.reports.currentMonthScope
import com.checkin.app.ui.reports.hasMonthAfter
import com.checkin.app.ui.reports.monthChosen
import com.checkin.app.ui.reports.nextMonth
import com.checkin.app.ui.reports.previousMonth
import com.checkin.app.ui.reports.resolve
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one place a range is clamped, so the one place a range can be wrong.
 *
 * Both the screen and the CSV export read this, and the export is the half nobody re-reads: it
 * gap-fills, so a window overrunning either end writes days out as days the user recorded nothing —
 * dates that have not happened at one end, dates before they had opened the app at the other.
 */
class ReportScopeTest {

    private fun month(y: Int, m: Int) = ReportScope.Month(YearMonth(y, m))
    private fun date(y: Int, m: Int, d: Int) = LocalDate(y, m, d)

    @Test
    fun `a fully past month resolves to the whole month`() {
        val window = month(2026, 6).resolve(
            trackingStart = date(2026, 1, 10),
            countedThrough = date(2026, 8, 25),
        )

        assertEquals(date(2026, 6, 1), window?.start)
        assertEquals(date(2026, 6, 30), window?.end)
        assertEquals(30, window?.days)
    }

    /**
     * The month in progress stops where counting does, not at the end of the calendar month.
     *
     * Unclamped, the screen would open every morning reporting the rest of the month as days already
     * missed, and an export mid-month would write dates that have not happened out as empty days.
     */
    @Test
    fun `the current month ends at the counted day rather than the month end`() {
        val window = month(2026, 8).resolve(
            trackingStart = date(2026, 1, 10),
            countedThrough = date(2026, 8, 25),
        )

        assertEquals(date(2026, 8, 1), window?.start)
        assertEquals(date(2026, 8, 25), window?.end)
        assertEquals(25, window?.days)
    }

    /** The month the record began in opens at the first session, not at the 1st. */
    @Test
    fun `a month holding the tracking start opens at it`() {
        val window = month(2026, 6).resolve(
            trackingStart = date(2026, 6, 12),
            countedThrough = date(2026, 8, 25),
        )

        assertEquals(date(2026, 6, 12), window?.start)
        assertEquals(date(2026, 6, 30), window?.end)
        assertEquals(19, window?.days)
    }

    @Test
    fun `all time runs the tracking start to the counted day`() {
        val window = ReportScope.AllTime.resolve(
            trackingStart = date(2026, 6, 12),
            countedThrough = date(2026, 8, 25),
        )

        assertEquals(date(2026, 6, 12), window?.start)
        assertEquals(date(2026, 8, 25), window?.end)
    }

    @Test
    fun `a month entirely before the record resolves to nothing`() {
        assertNull(
            month(2026, 3).resolve(
                trackingStart = date(2026, 6, 12),
                countedThrough = date(2026, 8, 25),
            ),
        )
    }

    @Test
    fun `a month entirely after the counted days resolves to nothing`() {
        assertNull(
            month(2026, 11).resolve(
                trackingStart = date(2026, 6, 12),
                countedThrough = date(2026, 8, 25),
            ),
        )
    }

    /**
     * The record's first day, checked in but not yet out: `countedThrough` is the day before it, so
     * every scope is empty. Rendering zeros here would assert the user showed up on none of some
     * number of days — a verdict on a day that is merely still in progress.
     */
    @Test
    fun `an unfinished first day leaves every scope empty`() {
        val today = date(2026, 6, 12)
        val yesterday = today.minus(1, DateTimeUnit.DAY)

        assertNull(month(2026, 6).resolve(trackingStart = today, countedThrough = yesterday))
        assertNull(ReportScope.AllTime.resolve(trackingStart = today, countedThrough = yesterday))
    }

    /** No sessions at all: no day is inside the record, so no scope reports missed days either. */
    @Test
    fun `a record with no tracking start resolves to nothing`() {
        assertNull(month(2026, 6).resolve(trackingStart = null, countedThrough = date(2026, 8, 25)))
        assertNull(ReportScope.AllTime.resolve(trackingStart = null, countedThrough = date(2026, 8, 25)))
    }

    /** A single counted day is a window, not an empty one — the boundary the null case sits beside. */
    @Test
    fun `a month whose first day is also the last counted day resolves to one day`() {
        val window = month(2026, 6).resolve(
            trackingStart = date(2026, 6, 1),
            countedThrough = date(2026, 6, 1),
        )

        assertEquals(date(2026, 6, 1), window?.start)
        assertEquals(date(2026, 6, 1), window?.end)
        assertEquals(1, window?.days)
    }

    @Test
    fun `reports opens on the month in progress and steps back across a year end`() {
        val today = LocalDate(2027, 1, 10)
        val opened = currentMonthScope(today)
        assertEquals(ReportScope.Month(YearMonth(2027, 1)), opened)
        assertEquals(ReportScope.Month(YearMonth(2026, 12)), opened.previousMonth(today))
        assertEquals(ReportScope.Month(YearMonth(2026, 12)), ReportScope.AllTime.previousMonth(today))
    }

    @Test
    fun `stepping forward stops at the month in progress and moves nothing under all time`() {
        val today = LocalDate(2027, 1, 10)
        val december = ReportScope.Month(YearMonth(2026, 12))
        assertTrue(december.hasMonthAfter(today))
        assertEquals(ReportScope.Month(YearMonth(2027, 1)), december.nextMonth(today))

        val january = ReportScope.Month(YearMonth(2027, 1))
        assertFalse(january.hasMonthAfter(today))
        assertEquals(january, january.nextMonth(today))
        assertFalse(ReportScope.AllTime.hasMonthAfter(today))
        assertEquals(ReportScope.AllTime, ReportScope.AllTime.nextMonth(today))
    }

    @Test
    fun `choosing month leaves all time for the month in progress and keeps a month shown`() {
        val today = LocalDate(2027, 1, 10)
        assertEquals(ReportScope.Month(YearMonth(2027, 1)), ReportScope.AllTime.monthChosen(today))
        val march = ReportScope.Month(YearMonth(2026, 3))
        assertEquals(march, march.monthChosen(today))
    }
}
