package com.checkin.app.ui.history

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarMonthTest {

    // 1 June 2026 is a Monday, 1 February 2026 a Sunday, 1 August 2026 a Saturday.
    private val june = YearMonth(2026, 6)

    private fun day(days: List<CalendarDay>, dayOfMonth: Int) = days[dayOfMonth - 1]

    @Test
    fun `the first day sits in the column its weekday takes under the week start`() {
        assertEquals(0, CalendarMonth.leadingBlanks(june, DayOfWeek.MONDAY))
        assertEquals(1, CalendarMonth.leadingBlanks(june, DayOfWeek.SUNDAY))
        assertEquals(2, CalendarMonth.leadingBlanks(june, DayOfWeek.SATURDAY))
        assertEquals(6, CalendarMonth.leadingBlanks(YearMonth(2026, 2), DayOfWeek.MONDAY))
    }

    @Test
    fun `a month takes four to six rows and a partial last week takes one`() {
        assertEquals(4, CalendarMonth.weekRows(YearMonth(2026, 2), DayOfWeek.SUNDAY))
        assertEquals(5, CalendarMonth.weekRows(YearMonth(2026, 2), DayOfWeek.MONDAY))
        assertEquals(6, CalendarMonth.weekRows(YearMonth(2026, 8), DayOfWeek.SUNDAY))
        assertEquals(5, CalendarMonth.weekRows(YearMonth(2026, 8), DayOfWeek.SATURDAY))
        assertEquals(5, CalendarMonth.weekRows(june, DayOfWeek.MONDAY))
    }

    @Test
    fun `every day of the month comes in order with its date key`() {
        val days = CalendarMonth.days(june, emptySet(), null, LocalDate(2026, 6, 15), LocalDate(2026, 6, 14))
        assertEquals(30, days.size)
        assertEquals("2026-06-01", days.first().dateKey)
        assertEquals("2026-06-30", days.last().dateKey)
        assertEquals(LocalDate(2026, 6, 9), day(days, 9).date)
    }

    @Test
    fun `a recorded day shades whatever it held and an empty day inside the record is not faded`() {
        val days = CalendarMonth.days(
            june,
            recordedKeys = setOf("2026-06-02", "2026-06-05"),
            trackingStart = LocalDate(2026, 6, 2),
            today = LocalDate(2026, 6, 15),
            countedThrough = LocalDate(2026, 6, 14),
        )
        assertTrue(day(days, 2).recorded)
        assertTrue(day(days, 5).recorded)
        assertFalse(day(days, 3).recorded)
        assertFalse(day(days, 3).outsideRecord, "a missed day is drawn plain, never faded")
    }

    @Test
    fun `only the future and the days before the first session are faded`() {
        val days = CalendarMonth.days(
            june,
            recordedKeys = setOf("2026-06-10"),
            trackingStart = LocalDate(2026, 6, 10),
            today = LocalDate(2026, 6, 15),
            countedThrough = LocalDate(2026, 6, 14),
        )
        assertTrue(day(days, 9).outsideRecord)
        assertFalse(day(days, 10).outsideRecord)
        assertFalse(day(days, 15).outsideRecord, "today is in the record")
        assertTrue(day(days, 16).outsideRecord)
    }

    @Test
    fun `with no session yet every day is outside the record and none shades`() {
        val days = CalendarMonth.days(
            june,
            recordedKeys = setOf("2026-06-03"),
            trackingStart = null,
            today = LocalDate(2026, 6, 15),
            countedThrough = LocalDate(2026, 6, 14),
        )
        assertTrue(days.all { it.outsideRecord })
        assertTrue(days.none { it.recorded })
    }

    @Test
    fun `today carries the marker and shades only once counted`() {
        val today = LocalDate(2026, 6, 15)
        val recorded = setOf("2026-06-15")
        val before = CalendarMonth.days(june, recorded, LocalDate(2026, 6, 1), today, LocalDate(2026, 6, 14))
        val after = CalendarMonth.days(june, recorded, LocalDate(2026, 6, 1), today, today)
        assertTrue(day(before, 15).today)
        assertFalse(day(before, 15).recorded)
        assertTrue(day(after, 15).recorded)
        assertEquals(listOf(today), after.filter { it.today }.map { it.date })
    }
}
