package com.checkin.app

import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.local.DailyAggregate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Showing up is the unit. These pin the thing that makes it worth counting: a short day counts
 * exactly as much as a long one, and nothing here ranks the days against each other.
 */
class ConsistencyStatsTest {

    private fun days(vararg entries: Pair<LocalDate, Long>): Map<String, DailyAggregate> =
        entries.associate { (date, ms) ->
            date.toString() to DailyAggregate(date.toString(), ms, 1, 0L, null, 0, 1, 0, 0)
        }

    private val minutes45 = 45 * 60_000L
    private val hours9 = 9 * 3_600_000L

    private fun day(n: Int) = LocalDate.of(2026, 6, n)

    // --- Totals ---

    @Test
    fun `showed-up days counts entries, whatever their length`() {
        val summaries = days(day(1) to minutes45, day(2) to hours9)

        assertEquals(2, ConsistencyStats.showedUpDays(summaries))
    }

    @Test
    fun `total worked sums every day`() {
        val summaries = days(day(1) to minutes45, day(2) to hours9)

        assertEquals(minutes45 + hours9, ConsistencyStats.totalWorkedMs(summaries))
    }

    @Test
    fun `total sessions counts every session, not every day`() {
        val summaries = mapOf(
            "2026-06-01" to DailyAggregate("2026-06-01", minutes45, 3, 0L, null, 0, 3, 0, 0),
            "2026-06-02" to DailyAggregate("2026-06-02", hours9, 1, 0L, null, 0, 1, 0, 0),
        )

        assertEquals(4, ConsistencyStats.totalSessions(summaries))
    }

    // --- How far counting reaches, which every window in the app ends at ---

    @Test
    fun `counting reaches today once it has a completed session`() {
        val summaries = days(day(13) to hours9, day(14) to hours9)

        assertEquals(day(14), ConsistencyStats.countedThrough(summaries, day(14)))
    }

    /**
     * The load-bearing half. An in-progress day never reaches the aggregates, so counting stops at
     * yesterday — which is what keeps a morning from opening with a missed day and a dipped total
     * that the first check-out would then take back.
     */
    @Test
    fun `counting stops at yesterday while today is unfinished`() {
        val summaries = days(day(12) to hours9, day(13) to hours9)

        assertEquals(day(13), ConsistencyStats.countedThrough(summaries, day(14)))
    }

    @Test
    fun `counting stops at yesterday with nothing recorded at all`() {
        assertEquals(day(13), ConsistencyStats.countedThrough(emptyMap(), day(14)))
    }
}
