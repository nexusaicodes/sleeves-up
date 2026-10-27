package com.checkin.app

import com.checkin.app.di.habitSyncRetryDelayMs
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The habits collector re-subscribes after a failed read for the life of the process, so its wait
 * must grow: a database that never opens is retried, and the nudge fallback re-armed, twice an hour
 * at most rather than every thirty seconds forever.
 */
class HabitSyncRetryTest {

    @Test
    fun `the wait doubles from thirty seconds`() {
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L), (0L..3L).map(::habitSyncRetryDelayMs))
    }

    @Test
    fun `the wait is capped at thirty minutes however many attempts fail`() {
        val cap = 30 * 60_000L
        assertEquals(cap, habitSyncRetryDelayMs(6L))
        assertEquals(cap, habitSyncRetryDelayMs(1_000L))
        assertEquals(cap, habitSyncRetryDelayMs(Long.MAX_VALUE))
    }
}
