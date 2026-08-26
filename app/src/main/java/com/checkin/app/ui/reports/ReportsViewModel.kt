package com.checkin.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.SessionBand
import com.checkin.app.data.SessionRhythm
import com.checkin.app.data.StartBucket
import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.ExportResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class ReportsUiState(
    val loading: Boolean = true,
    /** What span every figure below describes. The selector sets it; nothing else may narrow it. */
    val scope: ReportScope = ReportScope.AllTime,
    /** The day of the first session, or null until one exists. Read from the sessions, never stored. */
    val trackingStartDate: LocalDate? = null,
    /** The counted days [scope] covers, or null when it covers none — the empty state. */
    val window: ReportWindow? = null,
    val totalDays: Int = 0,
    val showedUpDays: Int = 0,
    val missedDays: Int = 0,
    /** Worked time over the scope, stated as a total and compared to nothing. */
    val totalWorkedMs: Long = 0L,
    val totalSessions: Int = 0,
    /**
     * Worked time per *tracked* day — missed days stay in the denominator.
     *
     * A quantity the screen prints as a row. It carried a ring on History's month card once and must
     * not carry one again: the only denominator available to fill an arc against is the user's own
     * all-time equivalent, which is a personal best under another name and ratchets.
     */
    val avgDailyMs: Long = 0L,
    /** Mean sessions per day shown up — a rhythm, not a score. */
    val avgSessionsPerDay: Float = 0f,
    /** Completed sessions by the hour they began. Descriptive; no bucket is preferable. */
    val startBuckets: Map<StartBucket, Int> = emptyMap(),
    /** Days grouped by how many sessions they held. Descriptive; more is not better. */
    val sessionBands: Map<SessionBand, Int> = emptyMap(),
    /** The scope's days, gap-filled so missed days read as zero rather than vanish. */
    val dailySeries: List<DayPoint> = emptyList(),
    /** Empty under a month scope: one bar is not a chart, so that card is not rendered. */
    val monthlySeries: List<MonthPoint> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val repository: CheckInRepository,
    private val timeSource: TimeSource,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val refresh = MutableStateFlow(0)

    // Opens on the current month rather than on the whole record: the live question is the month in
    // progress, and it is the scope History opens on too.
    private val scope = MutableStateFlow<ReportScope>(ReportScope.Month(YearMonth.from(timeSource.today())))

    // One-shot export outcomes — a Channel (not a StateFlow) so a config-change re-collect can't replay
    // a past result as a duplicate snackbar.
    private val exportChannel = Channel<ExportResult>(Channel.BUFFERED)
    val exportEvents: Flow<ExportResult> = exportChannel.receiveAsFlow()

    /**
     * The scope's figures, recomputed on DB writes, on refresh, at midnight, and when the scope moves.
     *
     * The tracking start is a DB read rather than a setting, so a record with no sessions behind it
     * cannot report days the user failed to show up for.
     */
    private val statsFlow: Flow<ReportsUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today -> repository.trackingStartFlow().map { today to it } }
        .flatMapLatest { (today, start) ->
            // A **one-day** query answers the only all-record question the window needs: has today
            // been checked out of yet. `countedThrough` is exactly "today's key is present", so the
            // whole record is never read just to find where counting stops.
            val todayKey = today.format(dateFormatter)
            val countedThroughFlow = repository.dailyAggregatesFlow(todayKey, todayKey)
                .map { ConsistencyStats.countedThrough(repository.byDateKey(it), today) }

            // Room invalidates per *table*, so the one-day query re-emits on every write to
            // `sessions` — including the ones that leave `countedThrough` exactly where it was.
            // Without this the `flatMapLatest` below tears down and re-subscribes both window
            // queries on each check-in and check-out, which under all time is the whole record.
            combine(scope, countedThroughFlow) { activeScope, countedThrough ->
                activeScope to activeScope.resolve(start, countedThrough)
            }.distinctUntilChanged().flatMapLatest { (activeScope, window) ->
                if (window == null) {
                    flowOf(ReportsUiState(loading = false, scope = activeScope, trackingStartDate = start))
                } else {
                    windowStats(activeScope, start, window)
                }
            }
        }

    val uiState: StateFlow<ReportsUiState> = statsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        // The tracking start comes from the DB, so there is nothing to seed it with synchronously.
        // The screen renders nothing while `loading` rather than flashing an empty state it is about
        // to replace.
        ReportsUiState(scope = scope.value),
    )

    /**
     * Every figure for one resolved [window].
     *
     * The two queries are **scoped identically**, and that is load-bearing rather than tidy: the
     * aggregates say which days count and the starts say when within them the work began, so a range
     * that differed between the two would let the start-time split describe sessions the rest of the
     * screen had excluded. The starts query exists at all because `DailyAggregate.firstCheckIn` is
     * each day's *first* session, so a day worked in three blocks would contribute one start.
     */
    private fun windowStats(
        activeScope: ReportScope,
        trackingStart: LocalDate?,
        window: ReportWindow,
    ): Flow<ReportsUiState> {
        val startKey = window.start.format(dateFormatter)
        val endKey = window.end.format(dateFormatter)
        return combine(
            repository.dailyAggregatesFlow(startKey, endKey),
            repository.sessionStartsFlow(startKey, endKey),
        ) { aggregates, sessionStarts ->
            val summaries = repository.byDateKey(aggregates)
            val showedUp = ConsistencyStats.showedUpDays(summaries)
            val totalWorkedMs = ConsistencyStats.totalWorkedMs(summaries)
            ReportsUiState(
                loading = false,
                scope = activeScope,
                trackingStartDate = trackingStart,
                window = window,
                totalDays = window.days,
                showedUpDays = showedUp,
                // Days with no sessions never reach the map, so the missed count is what is left of
                // the window once the recorded days are removed.
                missedDays = (window.days - showedUp).coerceAtLeast(0),
                totalWorkedMs = totalWorkedMs,
                totalSessions = ConsistencyStats.totalSessions(summaries),
                // Per *tracked* day, which keeps missed days in the denominator. `window.days` is
                // never zero — `resolve` returns null rather than an empty window.
                avgDailyMs = totalWorkedMs / window.days,
                avgSessionsPerDay = SessionRhythm.averageSessionsPerDay(summaries),
                startBuckets = SessionRhythm.startBuckets(sessionStarts, timeSource.zone()),
                sessionBands = SessionRhythm.sessionsPerDayBands(summaries),
                dailySeries = dailySeries(summaries, activeScope, window),
                monthlySeries = monthlySeries(summaries, activeScope, window),
                // `countedThrough` is consumed by `resolve` and is deliberately not carried into the
                // state: every figure here ends there by construction, so a second copy of it would
                // only be something for a caller to check the first against.
            )
        }
    }

    /**
     * The days plotted by the daily chart, gap-filled: a day without sessions is emitted as a real
     * zero, since a hole in a line reads as missing data rather than as an absence.
     *
     * A month scope plots the whole month; all time plots the trailing [DAILY_WINDOW_DAYS], because
     * a line with a point per day of a multi-year record is a solid block.
     */
    private fun dailySeries(
        summaries: Map<String, DailyAggregate>,
        activeScope: ReportScope,
        window: ReportWindow,
    ): List<DayPoint> {
        val from = when (activeScope) {
            is ReportScope.Month -> window.start
            ReportScope.AllTime -> maxOf(window.start, window.end.minusDays((DAILY_WINDOW_DAYS - 1).toLong()))
        }
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(window.end) }
            .map { day -> DayPoint(day, summaries[day.format(dateFormatter)]?.totalDurationMs ?: 0L) }
            .toList()
    }

    /**
     * Worked time per calendar month across the window, oldest first — **all time only**.
     *
     * A month scope spans one month, and a single bar states nothing a chart adds over the total
     * already printed above it, so the card is not rendered at all rather than rendered degenerate.
     */
    private fun monthlySeries(
        summaries: Map<String, DailyAggregate>,
        activeScope: ReportScope,
        window: ReportWindow,
    ): List<MonthPoint> {
        if (activeScope !is ReportScope.AllTime) return emptyList()
        // A malformed date_key is dropped rather than thrown on, matching how the repository and the
        // formatters read that column: one unparseable row must not strand the whole screen in
        // loading, on a table the app gives no way to edit.
        val totals = summaries.values.groupBy { monthOf(it.dateKey) }
            .filterKeys { it != null }
            .mapValues { (_, days) -> days.sumOf { it.totalDurationMs } }
        return generateSequence(YearMonth.from(window.start)) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(YearMonth.from(window.end)) }
            .map { month -> MonthPoint(month, totals[month] ?: 0L) }
            .toList()
    }

    private fun monthOf(dateKey: String): YearMonth? =
        runCatching { YearMonth.from(LocalDate.parse(dateKey, dateFormatter)) }.getOrNull()

    fun onResumed() {
        refresh.value++
    }

    fun previousMonth() {
        scope.value = ReportScope.Month(displayedMonth().minusMonths(1))
    }

    fun nextMonth() {
        scope.value = ReportScope.Month(displayedMonth().plusMonths(1))
    }

    /** Leaving all time returns to the month the user is in, not to whichever they last stepped to. */
    fun selectMonth() {
        scope.value = ReportScope.Month(YearMonth.from(timeSource.today()))
    }

    fun selectAllTime() {
        scope.value = ReportScope.AllTime
    }

    /**
     * Flips between all time and the current month.
     *
     * The decision reads [scope] rather than the rendered state, because the state only catches up
     * once the window's queries have emitted: judged from the screen, a second tap inside that gap
     * sees the scope it has already left and repeats the move instead of undoing it.
     */
    fun toggleAllTime() {
        if (scope.value is ReportScope.AllTime) selectMonth() else selectAllTime()
    }

    private fun displayedMonth(): YearMonth =
        (scope.value as? ReportScope.Month)?.month ?: YearMonth.from(timeSource.today())

    /**
     * Exports whatever the screen is showing.
     *
     * The range is the active scope's own window, so the file and the screen cannot describe
     * different days — they read the one `resolve`. That matters because the exporter gap-fills:
     * a range reaching past [ConsistencyStats.countedThrough] writes dates that have not happened
     * out as days the user recorded nothing, and one starting before the tracking start does the
     * same for days before they had opened the app.
     *
     * The clock is read **once**: a rollover between two readings would pair a start and an end
     * taken from different days.
     */
    fun exportCsv() {
        viewModelScope.launch {
            val today = timeSource.today()
            // Nothing recorded at all: there is no window to clamp to, let alone a day to write.
            val trackingStart = repository.trackingStart() ?: run {
                exportChannel.send(ExportResult.Nothing)
                return@launch
            }
            val todayKey = today.format(dateFormatter)
            val countedThrough = ConsistencyStats.countedThrough(
                repository.getDailySummaries(todayKey, todayKey),
                today,
            )
            // Tracking that began today, or a scope holding no counted day, leaves nothing to write.
            val window = scope.value.resolve(trackingStart, countedThrough) ?: run {
                exportChannel.send(ExportResult.Nothing)
                return@launch
            }
            val startStr = window.start.format(dateFormatter)
            val endStr = window.end.format(dateFormatter)
            val summaries = repository.getDailySummaries(startStr, endStr)
            // A window can be well-formed and still hold nothing: every check-in abandoned, or the
            // app installed and left idle. Exporting anyway would share a file that is pure
            // gap-fill — a document asserting absences the app never recorded.
            if (summaries.isEmpty()) {
                exportChannel.send(ExportResult.Nothing)
                return@launch
            }
            exportChannel.send(csvExporter.export(startStr, endStr, summaries))
        }
    }

    companion object {
        const val DAILY_WINDOW_DAYS = 30

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                ReportsViewModel(
                    container.repository,
                    container.timeSource,
                    container.csvExporter,
                )
            }
        }
    }
}
