package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.StringResolver
import com.checkin.app.service.SessionLifecycleRunner
import com.checkin.app.ui.checkin.CheckInViewModel
import com.checkin.app.ui.checkin.CheckOutSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        dao: FakeCheckInSessionDao,
        service: FakeServiceController,
        time: FakeTimeSource,
        engagement: FakeEngagementReporter = FakeEngagementReporter(),
        alarms: FakeSessionAlarms = FakeSessionAlarms(),
    ): CheckInViewModel {
        val repo = CheckInRepository(dao, time)
        // The real runner over fakes rather than a stand-in: the ViewModel owns the session's alarm
        // lifetime, and a stub would let the two drift without a test noticing.
        val reminder = SessionLifecycleRunner(
            repository = repo,
            notifier = FakeNotifier(),
            strings = StringResolver { "copy-$it" },
            alarms = alarms,
            log = FakeEngagementLog(),
            timeSource = time,
        )
        return CheckInViewModel(repo, time, service, reminder, engagement)
    }

    /**
     * Arming is the ViewModel's job, not the service's. `startTimer` can be refused — a restricted
     * standby bucket, an OEM that declines the foreground start — and a session that lost its
     * day-boundary close runs until the user notices, writing a multi-day duration onto a row the
     * app gives no way to edit.
     */
    @Test
    fun `check-in arms both session alarms even when the service start is refused`() = runTest {
        val dao = FakeCheckInSessionDao()
        val service = FakeServiceController().apply { startAllowed = false }
        val alarms = FakeSessionAlarms()
        val viewModel = buildViewModel(
            dao,
            service,
            FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)),
            alarms = alarms,
        )

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(1, alarms.reminders.size)
        assertEquals(1, alarms.dayBoundaries.size)
    }

    /**
     * `ServiceController.stop()` is a caught no-op when the service has already been killed, so
     * leaving the cancel to it would strand both alarms over a closed session.
     */
    @Test
    fun `check-out cancels the session alarms itself`() = runTest {
        val dao = FakeCheckInSessionDao()
        val alarms = FakeSessionAlarms()
        val viewModel = buildViewModel(
            dao,
            FakeServiceController(),
            FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)),
            alarms = alarms,
        )

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()
        viewModel.requestCheckOut()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        // Both instants cleared: check-in armed them, so only the check-out's cancel can have.
        assertEquals(0L, alarms.nextReminderAt)
        assertEquals(0L, alarms.dayBoundaryAt)
    }

    /**
     * The in-app button is one of two check-out writers, and it has to raise the celebration itself.
     * `MainActivity.onRootGatePassed` is the other; wiring only one leaves a check-out made from the
     * notification silently unacknowledged.
     */
    @Test
    fun `checking out from the screen raises the celebration`() = runTest {
        CheckOutSignal.clear()
        try {
            val dao = FakeCheckInSessionDao()
            val viewModel = buildViewModel(
                dao,
                FakeServiceController(),
                FakeTimeSource(0L, LocalDate.of(2026, 6, 15)),
            )

            backgroundScope.launch { viewModel.uiState.collect {} }
            viewModel.requestCheckIn()
            viewModel.onAuthSuccess()
            advanceUntilIdle()
            // Nothing to celebrate while the session is still open.
            assertNull(CheckOutSignal.completed.value)

            viewModel.requestCheckOut()
            viewModel.onAuthSuccess()
            advanceUntilIdle()

            assertNotNull(CheckOutSignal.completed.value)
            assertEquals(1, CheckOutSignal.completed.value?.daySessionCount)
        } finally {
            CheckOutSignal.clear()
        }
    }

    /**
     * The first check-in starts the record by being written, not by seeding anything beside it —
     * `hasEverTracked` reads the sessions table, so the inserted row is the whole of what marks
     * tracking as begun.
     */
    @Test
    fun `the first check-in inserts a session, starts the timer, and starts the record`() = runTest {
        val dao = FakeCheckInSessionDao()
        val service = FakeServiceController()
        val viewModel = buildViewModel(dao, service, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasEverTracked)

        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(1, dao.sessions.size)
        assertEquals(listOf(1L), service.started)
        assertTrue(viewModel.uiState.value.isRunning)
        assertTrue(viewModel.uiState.value.hasEverTracked)
    }

    /**
     * Every check-in is reported, not just the one a notification tap opened — otherwise a nudge the
     * user acted on from inside the app is never credited, and the stale notification is left posted.
     */
    @Test
    fun `an in-app check-in is reported to the engagement layer`() = runTest {
        val dao = FakeCheckInSessionDao()
        val engagement = FakeEngagementReporter()
        val viewModel = buildViewModel(
            dao,
            FakeServiceController(),
            FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)),
            engagement,
        )

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(listOf(1000L), engagement.checkedInAt)
    }

    /**
     * The initial state cannot answer "has this user tracked before" — it is a DB read away. So it
     * must not answer it wrongly either: it stays `loading`, and the screen holds the gauge's slot
     * rather than rendering the first-run welcome at a user with months of history.
     */
    @Test
    fun `an existing user is never reported as a first run, only as not yet loaded`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-01", startedAt = 0L, durationMs = 3_600_000L)
        val viewModel = buildViewModel(dao, FakeServiceController(), FakeTimeSource(0L, LocalDate.of(2026, 6, 15)))

        assertTrue(viewModel.uiState.value.loading)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
        assertTrue(viewModel.uiState.value.hasEverTracked)
    }

    @Test
    fun `day rollover advances today's date key without a resume`() = runTest {
        val dao = FakeCheckInSessionDao()
        val service = FakeServiceController()
        val time = FakeTimeSource(1000L, LocalDate.of(2026, 6, 15))
        val viewModel = buildViewModel(dao, service, time)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("2026-06-15", viewModel.uiState.value.todayDateKey)

        time.day.value = LocalDate.of(2026, 6, 16)
        advanceUntilIdle()

        assertEquals("2026-06-16", viewModel.uiState.value.todayDateKey)
    }

    @Test
    fun `a session open from a prior day keeps the screen running while today's list stays empty`() = runTest {
        val dao = FakeCheckInSessionDao()
        // Checked in yesterday, never checked out; the clock has since rolled to 06-15.
        dao.insertSession(
            com.checkin.app.data.local.CheckInSession(startedAt = 500L, dateKey = "2026-06-14"),
        )
        val viewModel =
            buildViewModel(dao, FakeServiceController(), FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // isRunning follows the ticker (the prior-day open row), guarding a double check-in, while
        // today's list stays empty — the interval belongs wholly to the day it began on.
        val state = viewModel.uiState.value
        assertTrue(state.isRunning)
        assertEquals(500L, state.currentSessionStartTime)
        assertEquals("2026-06-15", state.todayDateKey)
        assertTrue(state.todaySessions.isEmpty())
    }

    @Test
    fun `check-out closes the session and stops the timer`() = runTest {
        val dao = FakeCheckInSessionDao()
        val service = FakeServiceController()
        val viewModel = buildViewModel(dao, service, FakeTimeSource(1000L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.requestCheckIn()
        viewModel.onAuthSuccess()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isRunning)

        viewModel.requestCheckOut()
        viewModel.onAuthSuccess()
        advanceUntilIdle()

        assertEquals(1, service.stopCount)
        assertFalse(viewModel.uiState.value.isRunning)
        assertNotNull(dao.sessions.first().stoppedAt)
    }
}
