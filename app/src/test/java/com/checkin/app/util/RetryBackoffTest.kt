package com.checkin.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryBackoffTest {

    @Test
    fun `the wait doubles from thirty seconds`() {
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L), (0L..3L).map(::syncRetryDelayMs))
    }

    @Test
    fun `the wait is capped at thirty minutes however many attempts fail`() {
        val cap = 30 * 60_000L
        assertEquals(cap, syncRetryDelayMs(6L))
        assertEquals(cap, syncRetryDelayMs(1_000L))
        assertEquals(cap, syncRetryDelayMs(Long.MAX_VALUE))
    }
}
