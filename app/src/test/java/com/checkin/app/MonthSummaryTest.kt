package com.checkin.app

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.ui.history.components.DayIntensity
import com.checkin.app.ui.history.components.computeMonthTiles
import com.checkin.app.ui.history.components.statRatio
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthSummaryTest {

    private val hours8 = 8 * 3_600_000L
    private val minutes45 = 45 * 60_000L

    private fun day(key: String, ms: Long) = key to DailyAggregate(key, ms, 1, 0L, 0L)

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
        assertEquals(0L, tiles.peakDayMs)
    }

    @Test
    fun `the month peak is its longest day`() {
        val summaries = mapOf(
            day("2026-06-01", hours8),
            day("2026-06-02", minutes45),
            day("2026-06-10", 20 * 3_600_000L),
        )

        val tiles = computeMonthTiles(summaries, trackedDaysInMonth = 10)

        assertEquals(20 * 3_600_000L, tiles.peakDayMs)
    }

    // --- Ring fill, which is a month figure against the user's own all-time baseline ---

    @Test
    fun `a ring fills to the share of its baseline`() {
        assertEquals(0.5f, statRatio(4, 8), 0.001f)
        assertEquals(0.25f, statRatio(hours8 / 4, hours8), 0.001f)
    }

    /** Matching your own record fills the ring — there is nothing further for an arc to say. */
    @Test
    fun `matching or beating the baseline fills the ring and no more`() {
        assertEquals(1f, statRatio(9, 9), 0.001f)
        assertEquals(1f, statRatio(12, 9), 0.001f)
    }

    /**
     * The empty state the card is read in most often: nothing tracked yet, so every ring is a bare
     * track rather than a division by zero or a misleading full sweep.
     */
    @Test
    fun `an absent baseline empties the ring instead of dividing by zero`() {
        assertEquals(0f, statRatio(0, 0), 0.001f)
        assertEquals(0f, statRatio(5, 0), 0.001f)
        assertEquals(0f, statRatio(5L, -1L), 0.001f)
    }

    // --- Day intensity, which is what a calendar cell is drawn at ---

    @Test
    fun `the peak day reads at full strength`() {
        assertEquals(1f, DayIntensity.fractionOf(hours8, hours8), 0.001f)
    }

    /**
     * The floor is the point: showing up briefly must still be visibly a day showed up, not an
     * almost-empty cell that reads the same as not turning up at all.
     */
    @Test
    fun `a very short day is floored rather than faded to nothing`() {
        assertEquals(DayIntensity.MIN_FRACTION, DayIntensity.fractionOf(minutes45, 20 * 3_600_000L), 0.001f)
    }

    @Test
    fun `a day with no time is fully transparent`() {
        assertEquals(0f, DayIntensity.fractionOf(0L, hours8), 0.001f)
    }

    /** First day ever recorded, or a set of zero-length days: nothing to compare against. */
    @Test
    fun `a non-positive peak gives full strength rather than dividing by zero`() {
        assertEquals(1f, DayIntensity.fractionOf(hours8, 0L), 0.001f)
        assertEquals(1f, DayIntensity.fractionOf(hours8, -1L), 0.001f)
    }

    /** A day longer than the recorded peak (stale peak, mid-update) must not overflow the scale. */
    @Test
    fun `a day above the peak is clamped to full`() {
        assertEquals(1f, DayIntensity.fractionOf(hours8 * 2, hours8), 0.001f)
    }
}
