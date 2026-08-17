package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.ui.settings.DebugSnapshotReader
import com.checkin.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        log: FakeEngagementLog = FakeEngagementLog(),
        trigger: FakeNudgeTrigger = FakeNudgeTrigger(),
        alarms: FakeSessionAlarms = FakeSessionAlarms(),
        dao: FakeCheckInSessionDao = FakeCheckInSessionDao(),
        serviceRunning: Boolean = false,
    ): SettingsViewModel {
        val time = FixedTime(NOW, LocalDate.of(2026, 6, 15))
        val reader = DebugSnapshotReader(CheckInRepository(dao, time), alarms, time) { serviceRunning }
        return SettingsViewModel(log, trigger, reader)
    }

    @Test
    fun `the debug harness forces a send and runs a pass`() = runTest {
        val trigger = FakeNudgeTrigger()
        val viewModel = buildViewModel(trigger = trigger)

        viewModel.debugSend(Nudge.NOT_CHECKED_IN_BY, variant = 1)
        viewModel.debugRunPass()
        advanceUntilIdle()

        // The variant reaches the dispatcher: the harness exists to preview copy, and the install's
        // own bucket is fixed, so a dropped override would make every other wording unreachable.
        assertEquals(listOf(Nudge.NOT_CHECKED_IN_BY to 1), trigger.forced)
        assertEquals(1, trigger.runOnceCount)
    }

    /** The log is the whole record of what was sent — clearing it resets the daily cap with it. */
    @Test
    fun `clearing the log clears the send record`() = runTest {
        val log = FakeEngagementLog()
        val viewModel = buildViewModel(log)

        viewModel.debugClearLog()
        advanceUntilIdle()

        assertEquals(1, log.clearCount)
        assertEquals(0, log.shownCountSince(0L))
    }

    /**
     * The debug snapshot reads the session, the service flag and both armed instants through their
     * real seams. Pinned because nothing else in the app reads the alarm instants at all — they are
     * written at check-in and only ever read back by the repair path.
     */
    @Test
    fun `the snapshot reads the open session and the armed alarms`() = runTest {
        val dao = FakeCheckInSessionDao()
        val time = FixedTime(NOW, LocalDate.of(2026, 6, 15))
        CheckInRepository(dao, time).checkIn()
        val alarms = FakeSessionAlarms(remindersSent = 2).apply {
            scheduleReminderAt(NOW + 1000L)
            scheduleDayBoundaryAt(NOW + 5000L)
        }
        val viewModel = buildViewModel(dao = dao, alarms = alarms, serviceRunning = true)

        val snapshot = viewModel.readSnapshot(channels = emptyList())

        assertEquals("2026-06-15", snapshot.session?.dateKey)
        assertTrue(snapshot.serviceRunning)
        assertEquals(NOW + 1000L, snapshot.nextReminderAt)
        assertEquals(NOW + 5000L, snapshot.dayBoundaryAt)
        assertEquals(2, snapshot.remindersSent)
        // Derived from the session's own date_key, so a boundary armed for the wrong midnight shows.
        assertNotNull(snapshot.expectedDayBoundaryAt)
    }

    /**
     * The clipboard report reads the log directly rather than off `recentEvents`.
     *
     * That flow is `WhileSubscribed` and the log section is collapsed by default, so its `.value` is
     * the `emptyList()` seed in exactly the state the report is usually copied in — a report that
     * silently carried no log at all.
     */
    @Test
    fun `the log reads without the events flow being collected`() = runTest {
        val log = FakeEngagementLog()
        val viewModel = buildViewModel(log = log)
        log.record(Nudge.NOT_CHECKED_IN_BY, variant = 0, event = EngagementEventType.SHOWN, atMillis = NOW)

        // Nothing is collecting recentEvents here, which is the point.
        assertTrue(viewModel.recentEvents.value.isEmpty())
        assertEquals(1, viewModel.readLog().size)
    }

    /** With nothing open there is no session to read and no boundary to expect. */
    @Test
    fun `the snapshot reports no session when none is open`() = runTest {
        val viewModel = buildViewModel()

        val snapshot = viewModel.readSnapshot(channels = emptyList())

        assertNull(snapshot.session)
        assertNull(snapshot.expectedDayBoundaryAt)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
