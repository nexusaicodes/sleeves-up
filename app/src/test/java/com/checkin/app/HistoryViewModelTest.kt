package com.checkin.app

import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.ui.history.HistoryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(dao: FakeCheckInSessionDao, time: FixedTime): HistoryViewModel {
        val repo = CheckInRepository(dao, time)
        return HistoryViewModel(repo, time)
    }

    @Test
    fun `selectDay toggles the selection`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2026-06-01")
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectDay("2026-06-10")
        advanceUntilIdle()
        assertEquals("2026-06-10", viewModel.uiState.value.selectedDateKey)

        viewModel.selectDay("2026-06-10")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedDateKey)
    }

    /**
     * The record's very first day, counted the moment it is checked out of. This also pins the
     * denominator the showed-up ring fills against: an unfinished first day leaves zero tracked
     * days, and a ring measured against that must be empty rather than a division by zero.
     */
    @Test
    fun `the first day of the record counts as soon as it is checked out of`() = runTest {
        val dao = FakeCheckInSessionDao()
        val today = LocalDate.of(2026, 6, 15)
        val fourHours = 4 * 3_600_000L
        dao.seedOpen(today.toString())
        val viewModel = buildViewModel(dao, FixedTime(0L, today))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Checked in but never out: nothing is counted, and nothing divides by zero.
        assertTrue(viewModel.uiState.value.summaries.isEmpty())
        assertEquals(0, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(today.minusDays(1), viewModel.uiState.value.countedThrough)

        dao.seedCompleted(today.toString(), startedAt = 0L, durationMs = fourHours)
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.countedThrough)
        assertEquals(1, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(fourHours, viewModel.uiState.value.summaries.getValue(today.toString()).totalDurationMs)
    }

    /**
     * A check-out reaches the counted window on the spot, with no resume and no clock change — the
     * whole point of counting through today rather than through yesterday.
     */
    @Test
    fun `a check-out today lands in the stats without a resume`() = runTest {
        val dao = FakeCheckInSessionDao()
        val hour = 3_600_000L
        val today = LocalDate.of(2026, 6, 15)
        dao.seedCompleted("2026-06-13", startedAt = 0L, durationMs = 5 * hour)
        dao.seedCompleted("2026-06-14", startedAt = 0L, durationMs = 3 * hour)
        val viewModel = buildViewModel(dao, FixedTime(0L, today))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(2, viewModel.uiState.value.summaries.size)

        // An open session on today moves nothing: only completed ones aggregate.
        dao.seedOpen(today.toString(), startedAt = 0L)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(today.minusDays(1), viewModel.uiState.value.countedThrough)

        // Checking out extends the counted window and the month's own days at once.
        dao.seedCompleted(today.toString(), startedAt = 0L, durationMs = 7 * hour)
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.countedThrough)
        assertEquals(3, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(3, viewModel.uiState.value.summaries.size)
    }

    /** Midnight must not re-count or reset a day that was already counted at check-out. */
    @Test
    fun `a day counted at check-out is unchanged by the rollover that follows it`() = runTest {
        val dao = FakeCheckInSessionDao()
        val start = LocalDate.of(2026, 6, 15)
        val fourHours = 4 * 3_600_000L
        dao.seedCompleted(start.toString(), startedAt = 0L, durationMs = fourHours)
        val time = FixedTime(0L, start)
        val viewModel = buildViewModel(dao, time)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(fourHours, viewModel.uiState.value.summaries.getValue(start.toString()).totalDurationMs)

        time.day.value = LocalDate.of(2026, 6, 16)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 16), viewModel.uiState.value.today)
        // 06-15 is now a past day rather than a counted today, which must read identically.
        assertEquals(start, viewModel.uiState.value.countedThrough)
        assertEquals(1, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(fourHours, viewModel.uiState.value.summaries.getValue(start.toString()).totalDurationMs)
    }

    /**
     * Absent days stay in the tracked-day count, which is what the showed-up ring fills against —
     * so a month shown up for once reads as one day of many rather than as a full ring.
     */
    @Test
    fun `the tracked-day count keeps days with no sessions in it`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-01", startedAt = 0L, durationMs = 6 * 3_600_000L)
        // 06-02 and 06-03 have no sessions at all.
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 4)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Window is 06-01..06-03 — three tracked days, one of them shown up for.
        assertEquals(3, viewModel.uiState.value.trackedDaysInMonth)
        assertEquals(1, viewModel.uiState.value.summaries.size)
    }

    @Test
    fun `on the last calendar day of the month an unfinished today still does not count`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2026-06-01")
        // Today is June 30th (June's last day): monthEnd == today, so the in-progress day must not count.
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 30)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(29, viewModel.uiState.value.trackedDaysInMonth)
    }

    /** An empty ring is the honest reading of a month with nothing behind it — not a crash. */
    @Test
    fun `a month before tracking began reports no tracked days`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2026-06-01")
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.previousMonth()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.trackedDaysInMonth)
        assertTrue(viewModel.uiState.value.summaries.isEmpty())
    }

    /**
     * With nothing recorded there is no day the record covers, so no month reports tracked days —
     * and none reports missed ones either. A start that could exist without the sessions behind it
     * would shade a whole history of days the user had supposedly failed to show up for.
     */
    @Test
    fun `a record with no sessions has no tracking start and no tracked days`() = runTest {
        val dao = FakeCheckInSessionDao()
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.trackingStartDate)
        assertEquals(0, state.trackedDaysInMonth)
        assertTrue(state.summaries.isEmpty())
    }

    /** The first check-in starts the record with no separate write to remember it. */
    @Test
    fun `the tracking start appears as soon as a session exists`() = runTest {
        val dao = FakeCheckInSessionDao()
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.trackingStartDate)

        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 3_600_000L)
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 12), viewModel.uiState.value.trackingStartDate)
        // 06-12 .. 06-14: the tracked window opens at the first session, not at the month's start.
        assertEquals(3, viewModel.uiState.value.trackedDaysInMonth)
    }

    @Test
    fun `month navigation shifts the visible month and clears selection`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2026-06-01")
        val viewModel = buildViewModel(dao, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.selectDay("2026-06-10")
        advanceUntilIdle()
        viewModel.previousMonth()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 5), viewModel.uiState.value.currentMonth)
        assertNull(viewModel.uiState.value.selectedDateKey)
    }
}
