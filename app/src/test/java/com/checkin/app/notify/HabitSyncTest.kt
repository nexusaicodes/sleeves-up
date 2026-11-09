package com.checkin.app.notify

import com.checkin.app.data.local.Habit
import com.checkin.app.notify.HabitSync
import com.checkin.app.notify.habitSyncRetryDelayMs
import com.checkin.app.notify.nudge.GuardedNudges
import com.checkin.app.testing.FakeHabitChannels
import com.checkin.app.testing.FakeNudgeAlarms
import com.checkin.app.testing.FakeReminderSettings
import com.checkin.app.testing.FakeTimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The habits-table collector: what each emission reconciles, in order, and that a failed read is
 * retried with a growing wait rather than ending the sync.
 */
class HabitSyncTest {

    private val time = FakeTimeSource(0L, LocalDate.of(2026, 6, 15))
    private val channels = FakeHabitChannels()
    private val settings = FakeReminderSettings()
    private val alarms = FakeNudgeAlarms()
    private val events = mutableListOf<String>()

    private fun habit(id: Long, archived: Boolean = false) =
        Habit(id = id, uuid = "uuid-$id", name = "H$id", archived = archived, createdAt = 0L, channelId = "c$id")

    private fun coordinator(scope: kotlinx.coroutines.CoroutineScope) =
        GuardedNudges({}, { events += "rearm:${settings.active}" }, alarms, settings, time, scope) { false }

    @Test
    fun `each emission syncs channels, stores the active set, then re-arms`() = runTest(UnconfinedTestDispatcher()) {
        val table = MutableStateFlow(listOf(habit(1), habit(2, archived = true)))

        HabitSync(table, channels, settings, coordinator(backgroundScope), backgroundScope).start()

        assertEquals(listOf(listOf(habit(1), habit(2, archived = true))), channels.synced)
        assertEquals(setOf("uuid-1"), settings.active)
        // The re-arm reads the active set the same emission stored.
        assertEquals(listOf("rearm:[uuid-1]"), events)

        table.value = listOf(habit(1), habit(2, archived = true), habit(3))
        assertEquals(2, channels.synced.size)
        assertEquals(listOf("rearm:[uuid-1]", "rearm:[uuid-1, uuid-3]"), events)
    }

    @Test
    fun `a failed channel write still stores the active set and re-arms`() = runTest(UnconfinedTestDispatcher()) {
        val failingChannels = object : com.checkin.app.notify.HabitChannels {
            override fun sync(habits: List<Habit>) = error("channel service gone")
        }

        HabitSync(
            MutableStateFlow(listOf(habit(1))),
            failingChannels,
            settings,
            coordinator(backgroundScope),
            backgroundScope,
        )
            .start()

        assertEquals(setOf("uuid-1"), settings.active)
        assertEquals(1, events.size)
    }

    @Test
    fun `a failed read arms the fallback, waits, and subscribes again`() = runTest {
        var subscriptions = 0
        val table = flow {
            if (subscriptions++ == 0) error("database will not open")
            emit(listOf(habit(1)))
        }
        val waits = mutableListOf<Long>()

        HabitSync(table, channels, settings, coordinator(backgroundScope), backgroundScope, retryDelayMs = {
            waits += it
            1L
        }).start()
        runCurrent()
        testScheduler.advanceTimeBy(2L)
        runCurrent()

        assertEquals(1, alarms.armed.size)
        assertEquals(listOf(0L), waits)
        assertEquals(2, subscriptions)
        assertTrue(events.isNotEmpty())
    }

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
