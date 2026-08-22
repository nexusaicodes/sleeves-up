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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** A day's worked time, for the daily-hours chart. */
data class DayPoint(val date: LocalDate, val workedMs: Long)

/** A month's worked time, for the monthly-totals chart. */
data class MonthPoint(val month: YearMonth, val workedMs: Long)

data class ReportsUiState(
    val loading: Boolean = true,
    /** The day of the first session, or null until one exists. Read from the sessions, never stored. */
    val trackingStartDate: LocalDate? = null,
    val totalDays: Int = 0,
    val showedUpDays: Int = 0,
    val missedDays: Int = 0,
    /** Worked time over the whole record, stated as a total and compared to nothing. */
    val totalWorkedMs: Long = 0L,
    /** Mean sessions per day shown up — a rhythm, not a score. */
    val avgSessionsPerDay: Float = 0f,
    /** Completed sessions by the hour they began. Descriptive; no bucket is preferable. */
    val startBuckets: Map<StartBucket, Int> = emptyMap(),
    /** Days grouped by how many sessions they held. Descriptive; more is not better. */
    val sessionBands: Map<SessionBand, Int> = emptyMap(),
    /** Trailing window ending at the last counted day, gap-filled so missed days read as zero rather than vanish. */
    val dailySeries: List<DayPoint> = emptyList(),
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

    // One-shot export outcomes — a Channel (not a StateFlow) so a config-change re-collect can't replay
    // a past result as a duplicate snackbar.
    private val exportChannel = Channel<ExportResult>(Channel.BUFFERED)
    val exportEvents: Flow<ExportResult> = exportChannel.receiveAsFlow()

    // Overall stats through the last counted day, recomputed on DB writes, on refresh, and at midnight.
    // The tracking start is one of those DB reads rather than a setting, so a record with no sessions
    // behind it cannot report days the user failed to show up for.
    private val statsFlow: Flow<ReportsUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today -> repository.trackingStartFlow().map { today to it } }
        .flatMapLatest { (today, start) ->
            if (start == null || start.isAfter(today)) {
                flowOf(ReportsUiState(loading = false, trackingStartDate = start))
            } else {
                // Both queries run through today; how far of it counts is decided per emission, so a
                // check-out lands in every figure and every chart straight away. They are scoped
                // identically on purpose — the aggregates say which days count, the starts say when
                // within them the work began, and a range that differed between the two would let
                // the split describe sessions the rest of the screen had excluded.
                val startKey = start.format(dateFormatter)
                val todayKey = today.format(dateFormatter)
                combine(
                    repository.dailyAggregatesFlow(startKey, todayKey),
                    repository.sessionStartsFlow(startKey, todayKey),
                ) { aggregates, sessionStarts ->
                    val summaries = repository.byDateKey(aggregates)
                    val countedThrough = ConsistencyStats.countedThrough(summaries, today)
                    // The record's first day, still unfinished: nothing has been completed to
                    // report on, and the charts would otherwise plot a phantom zero day.
                    if (start.isAfter(countedThrough)) {
                        return@combine ReportsUiState(loading = false, trackingStartDate = start)
                    }
                    val totalDays = (countedThrough.toEpochDay() - start.toEpochDay() + 1).toInt()
                    val showedUp = ConsistencyStats.showedUpDays(summaries)
                    ReportsUiState(
                        loading = false,
                        trackingStartDate = start,
                        totalDays = totalDays,
                        showedUpDays = showedUp,
                        // Days with no sessions never reach the map, so the missed count is what
                        // is left of the tracked window once the recorded days are removed.
                        missedDays = (totalDays - showedUp).coerceAtLeast(0),
                        totalWorkedMs = ConsistencyStats.totalWorkedMs(summaries),
                        avgSessionsPerDay = SessionRhythm.averageSessionsPerDay(summaries),
                        startBuckets = SessionRhythm.startBuckets(sessionStarts, timeSource.zone()),
                        sessionBands = SessionRhythm.sessionsPerDayBands(summaries),
                        dailySeries = dailySeries(summaries, start, countedThrough),
                        monthlySeries = monthlySeries(summaries, start, countedThrough),
                    )
                }
            }
        }

    val uiState: StateFlow<ReportsUiState> = statsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        // The tracking start comes from the DB, so there is nothing to seed it with synchronously.
        // The screen renders nothing while `loading` rather than flashing an empty state it is about
        // to replace.
        ReportsUiState(),
    )

    /**
     * The trailing [DAILY_WINDOW_DAYS] days ending at [end], never starting before [start]. Days
     * without sessions are emitted as zero: a gap in a line chart reads as missing data, whereas an
     * absent day is a real zero.
     */
    private fun dailySeries(summaries: Map<String, DailyAggregate>, start: LocalDate, end: LocalDate): List<DayPoint> {
        val from = maxOf(start, end.minusDays((DAILY_WINDOW_DAYS - 1).toLong()))
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { day -> DayPoint(day, summaries[day.format(dateFormatter)]?.totalDurationMs ?: 0L) }
            .toList()
    }

    /** Worked time per calendar month over the trailing [MONTHLY_WINDOW_MONTHS], oldest first. */
    private fun monthlySeries(
        summaries: Map<String, DailyAggregate>,
        start: LocalDate,
        end: LocalDate,
    ): List<MonthPoint> {
        val firstMonth = maxOf(
            YearMonth.from(start),
            YearMonth.from(end).minusMonths((MONTHLY_WINDOW_MONTHS - 1).toLong()),
        )
        val lastMonth = YearMonth.from(end)
        // A malformed date_key is dropped rather than thrown on, matching how the repository and the
        // formatters read that column: one unparseable row must not strand the whole screen in
        // loading, on a table the app gives no way to edit.
        val totals = summaries.values.groupBy { monthOf(it.dateKey) }
            .filterKeys { it != null }
            .mapValues { (_, days) -> days.sumOf { it.totalDurationMs } }
        return generateSequence(firstMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(lastMonth) }
            .map { month -> MonthPoint(month, totals[month] ?: 0L) }
            .toList()
    }

    private fun monthOf(dateKey: String): YearMonth? =
        runCatching { YearMonth.from(LocalDate.parse(dateKey, dateFormatter)) }.getOrNull()

    fun onResumed() {
        refresh.value++
    }

    /**
     * Both ranges are bounded by the tracked window: they start no earlier than the tracking start
     * and end at the last counted day, never at either end of the calendar month.
     *
     * The exporter fills every gap day with zeros, so any day outside that window would be written
     * out as a day the user recorded nothing — a mid-month export would assert that for dates that
     * have not happened and for dates before they had ever used the app. Today is written only once
     * it holds a completed session, which is the same day every screen counts through; a file whose
     * last row is an empty today would be asserting an absence that is merely a day still in progress.
     */
    fun exportCsv(rangeType: ExportRange) {
        viewModelScope.launch {
            // One reading of the clock: a rollover between two of them would pair a start and an end
            // taken from different days.
            val today = timeSource.today()
            val month = YearMonth.from(today)
            // Nothing recorded at all: there is no window to clamp to, let alone a day to write.
            val trackingStart = repository.trackingStart() ?: run {
                exportChannel.send(ExportResult.Nothing)
                return@launch
            }
            // One day's aggregates answer whether today has been checked out of, through the same
            // rule the screens use, so the file and the screen it was exported from cannot disagree.
            val todayKey = today.format(dateFormatter)
            val countedThrough = ConsistencyStats.countedThrough(
                repository.getDailySummaries(todayKey, todayKey),
                today,
            )
            val (start, end) = when (rangeType) {
                ExportRange.THIS_MONTH ->
                    maxOf(month.atDay(1), trackingStart) to minOf(month.atEndOfMonth(), countedThrough)
                ExportRange.ALL_TIME -> trackingStart to countedThrough
            }
            // Tracking that began today, or an export on the 1st, leaves nothing completed to write.
            if (start.isAfter(end)) {
                exportChannel.send(ExportResult.Nothing)
                return@launch
            }
            val startStr = start.format(dateFormatter)
            val endStr = end.format(dateFormatter)
            val summaries = repository.getDailySummaries(startStr, endStr)
            // A range can be well-formed and still hold nothing: every check-in abandoned, or the app
            // installed and left idle. Exporting anyway would share a file that is pure gap-fill —
            // a document asserting absences the app never recorded.
            if (summaries.isEmpty()) {
                exportChannel.send(ExportResult.Nothing)
                return@launch
            }
            exportChannel.send(csvExporter.export(startStr, endStr, summaries))
        }
    }

    companion object {
        const val DAILY_WINDOW_DAYS = 30
        const val MONTHLY_WINDOW_MONTHS = 6

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

enum class ExportRange {
    THIS_MONTH,
    ALL_TIME,
}
