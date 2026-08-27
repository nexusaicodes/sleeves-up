package com.checkin.app

import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The trigger every screen's Room flows hang off, so what it emits decides how often those
 * subscriptions are torn down and rebuilt — see the KDoc on `dayTrigger` for the write that gets
 * lost in that window.
 */
class DayTriggerTest {

    private val day1 = LocalDate.of(2026, 8, 27)
    private val day2 = LocalDate.of(2026, 8, 28)

    @Test
    fun `a resume on the same day does not re-emit`() = runTest {
        val time = FakeTimeSource(now = 0L, date = day1)
        val refresh = MutableStateFlow(0)
        val seen = mutableListOf<LocalDate>()

        val job = launch { time.dayTrigger(refresh).toList(seen) }
        runCurrent()

        repeat(3) { refresh.value++ }
        runCurrent()

        // One emission, not four: the screen's Room subscriptions survive every resume.
        assertEquals(listOf(day1), seen)
        job.cancel()
    }

    @Test
    fun `a midnight rollover emits the new day`() = runTest {
        val time = FakeTimeSource(now = 0L, date = day1)
        val refresh = MutableStateFlow(0)
        val seen = mutableListOf<LocalDate>()

        val job = launch { time.dayTrigger(refresh).toList(seen) }
        runCurrent()

        time.day.value = day2
        runCurrent()

        assertEquals(listOf(day1, day2), seen)
        job.cancel()
    }

    @Test
    fun `a resume recomputes a day the sleeping rollover missed`() = runTest {
        // `currentDay` sleeps until midnight and a delay does not advance in deep sleep, so on
        // waking its value can be a day stale. The resume tick has to move the window anyway.
        val time = StaleDayTimeSource(stale = day1, actual = day2)
        val refresh = MutableStateFlow(0)
        val seen = mutableListOf<LocalDate>()

        val job = launch { time.dayTrigger(refresh).toList(seen) }
        runCurrent()

        assertEquals(listOf(day2), seen)
        job.cancel()
    }
}

/**
 * A clock whose rollover flow is a day behind its wall clock — the state a device wakes in when the
 * `delay` inside `currentDay` was frozen through midnight by deep sleep.
 */
private class StaleDayTimeSource(private val stale: LocalDate, private val actual: LocalDate) : TimeSource {
    override fun nowMillis(): Long = 0L
    override fun today(): LocalDate = actual
    override fun currentDay(): Flow<LocalDate> = flowOf(stale)
}
