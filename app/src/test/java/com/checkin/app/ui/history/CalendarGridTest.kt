package com.checkin.app.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.util.Locale

/**
 * The calendar's row count, which `HistoryScreen` divides the viewport by before the grid is drawn.
 * It turns on the locale's week start: the same month can need a row more or fewer.
 */
class CalendarGridTest {

    private val sundayStart = Locale.US
    private val mondayStart = Locale.UK

    @Test
    fun `a February starting on the week's first day fills exactly four rows`() {
        assertEquals(4, weekRowsIn(YearMonth.of(2026, 2), sundayStart))
        assertEquals(4, weekRowsIn(YearMonth.of(2027, 2), mondayStart))
    }

    @Test
    fun `the same month takes a row more when the week starts a day later`() {
        assertEquals(5, weekRowsIn(YearMonth.of(2026, 2), mondayStart))
        assertEquals(5, weekRowsIn(YearMonth.of(2026, 3), sundayStart))
        assertEquals(6, weekRowsIn(YearMonth.of(2026, 3), mondayStart))
    }

    @Test
    fun `a long month starting late in the week takes six rows`() {
        assertEquals(6, weekRowsIn(YearMonth.of(2026, 8), sundayStart))
        assertEquals(6, weekRowsIn(YearMonth.of(2026, 8), mondayStart))
    }
}
