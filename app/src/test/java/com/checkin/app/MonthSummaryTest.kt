package com.checkin.app

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.history.components.computeMonthTiles
import com.checkin.app.ui.history.components.showedUpRatio
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthSummaryTest {

    private val hours8 = 8 * 3_600_000L
    private val minutes45 = 45 * 60_000L

    private fun day(key: String, ms: Long, sessions: Int = 1) = key to DailyAggregate(key, ms, sessions, 0L, 0L, 0)

    // --- Month tiles ---

    @Test
    fun `showed-up counts every day with sessions and missed is the rest of the window`() {
        val summaries = mapOf(day("2026-06-01", hours8), day("2026-06-02", minutes45))

        val tiles = computeMonthTiles(summaries, trackedDaysInMonth = 9)

        assertEquals(2, tiles.showedUp)
        assertEquals(7, tiles.missed)
    }

    /**
     * The tiles count whatever the caller hands them, because an in-progress today is absent from
     * the map to begin with — the aggregate queries keep only completed sessions. A day that has
     * been checked out of therefore arrives here like any other, and the caller widens
     * `trackedDaysInMonth` by the same day through the same rule.
     */
    @Test
    fun `a day checked out of today counts like any other`() {
        val summaries = mapOf(day("2026-06-01", hours8), day("2026-06-10", hours8))

        val tiles = computeMonthTiles(summaries, trackedDaysInMonth = 10)

        assertEquals(2, tiles.showedUp)
        assertEquals(8, tiles.missed)
    }

    /**
     * The average divides by *tracked* days, not by days with sessions, so missed days stay in the
     * denominator — otherwise showing up once a month would report a perfect daily average.
     */
    @Test
    fun `the average keeps missed days in the denominator`() {
        val summaries = mapOf(day("2026-06-01", hours8))

        val tiles = computeMonthTiles(summaries, trackedDaysInMonth = 8)

        assertEquals(hours8 / 8, tiles.avgDailyMs)
    }

    @Test
    fun `missed never goes negative when the window is shorter than the recorded days`() {
        val summaries = mapOf(day("2026-06-01", hours8), day("2026-06-02", hours8))

        val tiles = computeMonthTiles(summaries, trackedDaysInMonth = 1)

        assertEquals(0, tiles.missed)
    }

    @Test
    fun `an empty month averages zero rather than dividing by zero`() {
        val tiles = computeMonthTiles(emptyMap(), trackedDaysInMonth = 0)

        assertEquals(0L, tiles.avgDailyMs)
        assertEquals(0, tiles.showedUp)
        assertEquals(0f, tiles.avgSessionsPerDay, 0.001f)
    }

    /**
     * The other average divides the other way — by the days that *had* sessions — because it
     * describes the shape of a working day rather than how many of them there were.
     */
    @Test
    fun `sessions per day divides by the days that had sessions`() {
        val summaries = mapOf(
            day("2026-06-01", hours8, sessions = 3),
            day("2026-06-02", minutes45, sessions = 1),
        )

        val tiles = computeMonthTiles(summaries, trackedDaysInMonth = 20)

        assertEquals(2f, tiles.avgSessionsPerDay, 0.001f)
    }

    // --- The one ring fill left, and its denominator is the month rather than a personal best ---

    @Test
    fun `the showed-up ring fills to the share of the month that was shown up for`() {
        assertEquals(0.5f, showedUpRatio(4, 8), 0.001f)
        assertEquals(0.25f, showedUpRatio(5, 20), 0.001f)
    }

    /** Every tracked day shown up for fills the ring; an arc has nowhere further to go. */
    @Test
    fun `a fully shown-up month fills the ring and no more`() {
        assertEquals(1f, showedUpRatio(9, 9), 0.001f)
        assertEquals(1f, showedUpRatio(12, 9), 0.001f)
    }

    /**
     * The empty state the card is read in most often: nothing tracked yet, so the ring is a bare
     * track rather than a division by zero or a misleading full sweep.
     */
    @Test
    fun `a month with no tracked days empties the ring instead of dividing by zero`() {
        assertEquals(0f, showedUpRatio(0, 0), 0.001f)
        assertEquals(0f, showedUpRatio(5, 0), 0.001f)
        assertEquals(0f, showedUpRatio(5, -1), 0.001f)
    }
}
