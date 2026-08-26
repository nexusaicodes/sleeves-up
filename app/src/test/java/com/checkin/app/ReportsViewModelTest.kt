package com.checkin.app

import com.checkin.app.data.SessionBand
import com.checkin.app.data.StartBucket
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.platform.ExportResult
import com.checkin.app.ui.reports.DayPoint
import com.checkin.app.ui.reports.MonthPoint
import com.checkin.app.ui.reports.ReportScope
import com.checkin.app.ui.reports.ReportsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun buildViewModel(
        dao: FakeCheckInSessionDao,
        exporter: FakeCsvExporter,
        time: FixedTime,
    ): ReportsViewModel {
        val repo = CheckInRepository(dao, time)
        return ReportsViewModel(repo, time, exporter)
    }

    @Test
    fun `tracking that starts today yields all-zero stats`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2026-06-15")
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.totalDays)
        assertEquals(0, state.showedUpDays)
        assertEquals(0, state.missedDays)
        // Nothing to plot yet — the charts must render an empty series, not a phantom zero day.
        assertEquals(emptyList<DayPoint>(), state.dailySeries)
        assertEquals(emptyList<MonthPoint>(), state.monthlySeries)
    }

    /**
     * The start is read off the sessions, so with no rows there is no start — and therefore no
     * window to count days against. A start that could exist without the sessions behind it would
     * report every day since as one the user failed to show up for, on a device holding no history.
     */
    @Test
    fun `a record with no sessions reports no tracked days and no missed days`() = runTest {
        val dao = FakeCheckInSessionDao()
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.trackingStartDate)
        assertEquals(0, state.totalDays)
        assertEquals(0, state.missedDays)
        assertEquals(emptyList<DayPoint>(), state.dailySeries)
    }

    /** The start is the first session's day, whichever order the rows arrive in. */
    @Test
    fun `the tracking start is the earliest session's day`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 3_600_000L)
        dao.seedCompleted("2026-06-03", startedAt = 0L, durationMs = 3_600_000L)
        dao.seedCompleted("2026-06-09", startedAt = 0L, durationMs = 3_600_000L)
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 3), viewModel.uiState.value.trackingStartDate)
        // 06-03 .. 06-14 inclusive; three of those twelve days hold a session.
        assertEquals(12, viewModel.uiState.value.totalDays)
        assertEquals(3, viewModel.uiState.value.showedUpDays)
    }

    /** A check-in opened today starts the record immediately, before it has been checked out. */
    @Test
    fun `an open session counts as the tracking start`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2026-06-10")
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 6, 10), viewModel.uiState.value.trackingStartDate)
        // It contributes nothing to any total until it closes, so all five days read as missed.
        assertEquals(5, viewModel.uiState.value.totalDays)
        assertEquals(0, viewModel.uiState.value.showedUpDays)
    }

    @Test
    fun `days with no sessions are counted as missed rather than dropped`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 8 * 3_600_000L)
        val start = LocalDate.of(2026, 6, 10)
        dao.seedOpen(start.toString())
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // 5 tracked days, one showed up for — the other four never reach the map at all.
        assertEquals(5, state.totalDays)
        assertEquals(1, state.showedUpDays)
        assertEquals(4, state.missedDays)
    }

    @Test
    fun `the daily series gap-fills absent days with zero and ends at the last counted day`() = runTest {
        val dao = FakeCheckInSessionDao()
        val eightHours = 8 * 3_600_000L
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = eightHours)
        val start = LocalDate.of(2026, 6, 10)
        dao.seedOpen(start.toString())
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val series = viewModel.uiState.value.dailySeries
        // Window is clamped to the tracking start, and today (06-15) is still unfinished.
        assertEquals(start, series.first().date)
        assertEquals(LocalDate.of(2026, 6, 14), series.last().date)
        assertEquals(5, series.size)
        assertEquals(eightHours, series.first { it.date == LocalDate.of(2026, 6, 12) }.workedMs)
        // A day with no sessions is a real zero, not a hole in the line.
        assertEquals(0L, series.first { it.date == LocalDate.of(2026, 6, 11) }.workedMs)
    }

    /**
     * Days shown up is the figure a user watches, and it is the reason counting reaches today at
     * all: checking out has to extend it there and then, not at the next midnight.
     */
    @Test
    fun `a check-out today extends the counts and the daily series straight away`() = runTest {
        val dao = FakeCheckInSessionDao()
        val hour = 3_600_000L
        val today = LocalDate.of(2026, 6, 15)
        dao.seedCompleted("2026-06-13", startedAt = 0L, durationMs = hour)
        dao.seedCompleted("2026-06-14", startedAt = 0L, durationMs = hour)
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, today))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.showedUpDays)
        assertEquals(2, viewModel.uiState.value.totalDays)
        assertEquals(LocalDate.of(2026, 6, 14), viewModel.uiState.value.dailySeries.last().date)

        // Checked in but not out: nothing must move on an intention.
        dao.seedOpen(today.toString(), startedAt = 0L)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.showedUpDays)

        dao.seedCompleted(today.toString(), startedAt = 0L, durationMs = 2 * hour)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.showedUpDays)
        assertEquals(4 * hour, viewModel.uiState.value.totalWorkedMs)
        assertEquals(3, viewModel.uiState.value.totalDays)
        assertEquals(0, viewModel.uiState.value.missedDays)
        val series = viewModel.uiState.value.dailySeries
        assertEquals(today, series.last().date)
        assertEquals(2 * hour, series.last().workedMs)
    }

    /**
     * All time cannot plot a point per day of a multi-year record, so it keeps a trailing window.
     * A month scope has no such problem and plots the month, which the next test pins.
     */
    @Test
    fun `the all-time daily series is capped to its trailing window`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedOpen("2025-01-01")
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.selectAllTime()
        advanceUntilIdle()

        val series = viewModel.uiState.value.dailySeries
        assertEquals(ReportsViewModel.DAILY_WINDOW_DAYS, series.size)
        assertEquals(LocalDate.of(2026, 6, 14), series.last().date)
    }

    /**
     * A month scope plots the month itself, uncapped — 30 days is not the rule, it is all time's rule.
     *
     * The February session is what puts the whole of March inside the record: seeded from March
     * alone, the month would correctly clamp to its own tracking start and cover 22 days, which is
     * the case the next test pins.
     */
    @Test
    fun `a fully covered past month plots every day of that month`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-02-01", startedAt = 0L, durationMs = 3_600_000L)
        dao.seedCompleted("2026-03-10", startedAt = 0L, durationMs = 3_600_000L)
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        repeat(3) { viewModel.previousMonth() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportScope.Month(YearMonth.of(2026, 3)), state.scope)
        assertEquals(31, state.totalDays)
        assertEquals(LocalDate.of(2026, 3, 1), state.dailySeries.first().date)
        assertEquals(LocalDate.of(2026, 3, 31), state.dailySeries.last().date)
        assertEquals(1, state.showedUpDays)
        // A month spans one bar, which is not a chart — the series is empty and the card is absent.
        assertEquals(emptyList<MonthPoint>(), state.monthlySeries)
    }

    /**
     * A month the record only partly covers opens at the first session, not at the 1st — the same
     * clamp the CSV export depends on, since gap-filling days before the user had ever opened the
     * app would write them out as days they recorded nothing.
     */
    @Test
    fun `a month holding the tracking start opens at it rather than at the first`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-03-10", startedAt = 0L, durationMs = 3_600_000L)
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        repeat(3) { viewModel.previousMonth() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(LocalDate.of(2026, 3, 10), state.dailySeries.first().date)
        assertEquals(LocalDate.of(2026, 3, 31), state.dailySeries.last().date)
        assertEquals(22, state.totalDays)
    }

    /** Stepping outside the record leaves an empty scope, not a set of zeros over invented days. */
    @Test
    fun `a month before the record resolves to an empty scope`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-10", startedAt = 0L, durationMs = 3_600_000L)
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.previousMonth()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.window)
        assertEquals(0, state.totalDays)
        // The record itself is still known — the scope is empty, not the app.
        assertEquals(LocalDate.of(2026, 6, 10), state.trackingStartDate)
    }

    /** All time only, and every month between the first session and the last counted day. */
    @Test
    fun `the monthly series covers every month in the window including empty ones`() = runTest {
        val dao = FakeCheckInSessionDao()
        val fourHours = 4 * 3_600_000L
        dao.seedCompleted("2026-04-02", startedAt = 0L, durationMs = fourHours)
        dao.seedOpen("2026-04-01")
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.selectAllTime()
        advanceUntilIdle()

        val series = viewModel.uiState.value.monthlySeries
        assertEquals(
            listOf(YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6)),
            series.map {
                it.month
            },
        )
        assertEquals(fourHours, series[0].workedMs)
        assertEquals(0L, series[1].workedMs) // May had no sessions but still needs a bar
    }

    /**
     * A `date_key` that will not parse is dropped from the monthly roll-up rather than thrown on.
     * The throw would escape the `map` backing `uiState` and strand the whole screen in `loading` —
     * permanently, since sessions are immutable and nothing in the app can delete the offending row.
     *
     * The key is chosen to sort inside the queried range so it genuinely reaches the roll-up; the
     * daily series is unaffected either way, since it looks days up by key rather than parsing them.
     */
    @Test
    fun `an unparseable date key is dropped rather than stranding the screen`() = runTest {
        val dao = FakeCheckInSessionDao()
        val fourHours = 4 * 3_600_000L
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = fourHours)
        dao.seedCompleted("2026-06-13x", startedAt = 0L, durationMs = 9 * 3_600_000L)
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.selectAllTime()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        // June carries the parseable day alone — the corrupt row contributes nothing to its bar.
        assertEquals(fourHours, state.monthlySeries.single { it.month == YearMonth.of(2026, 6) }.workedMs)
    }

    /** A short day counts as showing up exactly as much as a long one — there is no bar to clear. */
    @Test
    fun `a 45-minute day counts as a day showed up`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-14", startedAt = 0L, durationMs = 45 * 60_000L)
        val start = LocalDate.of(2026, 6, 10)
        dao.seedOpen(start.toString())
        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.totalDays) // 2026-06-10 .. 2026-06-14 inclusive
        // No length threshold stands between a day and counting, here or anywhere else.
        assertEquals(1, state.showedUpDays)
        assertEquals(4, state.missedDays)
    }

    /**
     * The two descriptive splits, over the same window as everything else on the screen.
     *
     * The start-time split needs every session, not each day's first: a day worked in three blocks
     * contributes three starts, which is exactly what the per-day aggregates cannot say.
     */
    @Test
    fun `the splits describe every completed session in the window`() = runTest {
        val dao = FakeCheckInSessionDao()
        val hour = 3_600_000L
        val today = LocalDate.of(2026, 6, 15)
        // 06-14, worked in three blocks: 09:00 morning, 13:00 afternoon, 20:00 evening.
        val midnight = LocalDate.of(2026, 6, 14).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        dao.seedCompleted("2026-06-14", startedAt = midnight + 9 * hour, durationMs = hour)
        dao.seedCompleted("2026-06-14", startedAt = midnight + 13 * hour, durationMs = hour)
        dao.seedCompleted("2026-06-14", startedAt = midnight + 20 * hour, durationMs = hour)
        // 06-13, one morning block.
        val before = LocalDate.of(2026, 6, 13).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        dao.seedCompleted("2026-06-13", startedAt = before + 10 * hour, durationMs = hour)

        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, today))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.startBuckets[StartBucket.MORNING])
        assertEquals(1, state.startBuckets[StartBucket.AFTERNOON])
        assertEquals(1, state.startBuckets[StartBucket.EVENING])
        assertEquals(1, state.sessionBands[SessionBand.ONE])
        assertEquals(0, state.sessionBands[SessionBand.TWO])
        assertEquals(1, state.sessionBands[SessionBand.THREE_PLUS])
        assertEquals(2f, state.avgSessionsPerDay, 0.001f)
        assertEquals(4 * hour, state.totalWorkedMs)
    }

    /** An open session is in no split, exactly as it is in no total. */
    @Test
    fun `an open session contributes to neither split`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-13", startedAt = 0L, durationMs = 3_600_000L)
        dao.seedOpen("2026-06-15", startedAt = 9 * 3_600_000L)

        val viewModel = buildViewModel(dao, FakeCsvExporter(), FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.startBuckets.values.sum())
        assertEquals(1, state.sessionBands[SessionBand.ONE])
    }

    @Test
    fun `export invokes the exporter and emits the result once`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        dao.seedOpen("2026-06-10")
        val viewModel = buildViewModel(dao, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))
        backgroundScope.launch { viewModel.uiState.collect {} }

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        viewModel.selectAllTime()
        viewModel.exportCsv()
        advanceUntilIdle()

        assertNotNull(exporter.lastRange)
        assertEquals(listOf(ExportResult.Success), events)
    }

    @Test
    fun `a consumed export event does not replay to a later collector`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-12", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        dao.seedOpen("2026-06-10")
        val viewModel = buildViewModel(dao, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))

        // First collector receives the event, then goes away (e.g. the screen is recreated).
        val first = mutableListOf<ExportResult>()
        val job = launch { viewModel.exportEvents.collect { first += it } }
        viewModel.selectAllTime()
        viewModel.exportCsv()
        advanceUntilIdle()
        job.cancel()

        // A later collector (post-config-change re-subscribe) gets no replay of the past result.
        val second = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { second += it } }
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Success), first)
        assertEquals(emptyList<ExportResult>(), second)
    }

    // The exporter fills every gap day with zeros, so a range reaching past the last completed day
    // writes rows for days that were never worked — or that have not happened yet.

    @Test
    fun `a mid-month export stops at the last counted day, not at the end of the month`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-05", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        dao.seedOpen("2026-01-01")
        val viewModel = buildViewModel(
            dao,
            exporter,
            FixedTime(0L, LocalDate.of(2026, 6, 15)),
        )

        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals("2026-06-01" to "2026-06-14", exporter.lastRange)
    }

    /**
     * The month is clamped at both ends, not just the later one: days before the user had ever used
     * the app are not absences, and gap-filling them would contradict the same user's all-time export.
     */
    @Test
    fun `a mid-month export starts at the tracking start, not the first of the month`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-21", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        dao.seedOpen("2026-06-20")
        val viewModel = buildViewModel(
            dao,
            exporter,
            FixedTime(0L, LocalDate.of(2026, 6, 25)),
        )

        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals("2026-06-20" to "2026-06-24", exporter.lastRange)
    }

    @Test
    fun `an all-time export excludes a today that is still being worked`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-05-01", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        dao.seedOpen("2026-04-20")
        val viewModel = buildViewModel(
            dao,
            exporter,
            FixedTime(0L, LocalDate.of(2026, 6, 15)),
        )

        viewModel.selectAllTime()
        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals("2026-04-20" to "2026-06-14", exporter.lastRange)
    }

    /**
     * Once today has been checked out of it is a day like any other, and the file has to say so —
     * a CSV whose last row stopped short of a day the screen was already counting would contradict
     * the screen it was exported from.
     */
    @Test
    fun `an export reaches today once it has been checked out of`() = runTest {
        val dao = FakeCheckInSessionDao()
        val today = LocalDate.of(2026, 6, 15)
        dao.seedCompleted("2026-06-05", startedAt = 0L, durationMs = 3_600_000L)
        dao.seedCompleted(today.toString(), startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val viewModel = buildViewModel(dao, exporter, FixedTime(0L, today))

        viewModel.exportCsv()
        advanceUntilIdle()
        assertEquals("2026-06-05" to "2026-06-15", exporter.lastRange)

        viewModel.selectAllTime()
        viewModel.exportCsv()
        advanceUntilIdle()
        assertEquals("2026-06-05" to "2026-06-15", exporter.lastRange)
    }

    /**
     * The export follows the scope, which is what makes an arbitrary past month exportable at all —
     * the old This month / All time pair could not express one.
     *
     * It also means the file and the screen read the one `resolve`, so they cannot describe
     * different days. They used to clamp separately, with their own arithmetic.
     */
    @Test
    fun `an export under a past month scope writes that month's clamped window`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-02-01", startedAt = 0L, durationMs = 3_600_000L)
        dao.seedCompleted("2026-03-10", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val viewModel = buildViewModel(dao, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))

        repeat(3) { viewModel.previousMonth() }
        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals("2026-03-01" to "2026-03-31", exporter.lastRange)
    }

    /** A scope holding no counted day writes no file, exactly as an empty range does. */
    @Test
    fun `an export under an empty scope reports nothing`() = runTest {
        val dao = FakeCheckInSessionDao()
        dao.seedCompleted("2026-06-10", startedAt = 0L, durationMs = 3_600_000L)
        val exporter = FakeCsvExporter(ExportResult.Success)
        val viewModel = buildViewModel(dao, exporter, FixedTime(0L, LocalDate.of(2026, 6, 15)))

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        // A month entirely before the record began.
        viewModel.previousMonth()
        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Nothing), events)
        assertNull(exporter.lastRange)
    }

    /**
     * A range can be well-formed and hold nothing — every check-in abandoned, or the app installed
     * and left idle. The file would then be pure gap-fill: a document asserting a week of absences
     * the app never recorded.
     */
    @Test
    fun `a valid range holding no completed session reports nothing`() = runTest {
        val exporter = FakeCsvExporter(ExportResult.Success)
        val dao = FakeCheckInSessionDao()
        // A check-in that was opened and abandoned: tracking has begun, but no day is complete.
        dao.seedOpen("2026-06-08")
        val viewModel = buildViewModel(
            dao,
            exporter,
            FixedTime(0L, LocalDate.of(2026, 6, 15)),
        )

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        viewModel.selectAllTime()
        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Nothing), events)
        assertNull(exporter.lastRange)
    }

    @Test
    fun `an export with no completed day reports nothing rather than writing absences`() = runTest {
        val exporter = FakeCsvExporter(ExportResult.Success)
        val dao = FakeCheckInSessionDao()
        // Tracking began today, so there is no completed day in either range.
        dao.seedOpen("2026-06-01")
        val viewModel = buildViewModel(
            dao,
            exporter,
            FixedTime(0L, LocalDate.of(2026, 6, 1)),
        )

        val events = mutableListOf<ExportResult>()
        backgroundScope.launch { viewModel.exportEvents.collect { events += it } }

        viewModel.exportCsv()
        viewModel.selectAllTime()
        viewModel.exportCsv()
        advanceUntilIdle()

        assertEquals(listOf(ExportResult.Nothing, ExportResult.Nothing), events)
        assertNull(exporter.lastRange)
    }
}
