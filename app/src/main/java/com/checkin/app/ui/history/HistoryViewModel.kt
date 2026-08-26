package com.checkin.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.repository.CheckInRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class HistoryUiState(
    val currentMonth: YearMonth,
    /** The day of the first session, or null until one exists. Read from the sessions, never stored. */
    val trackingStartDate: LocalDate?,
    val today: LocalDate,
    /**
     * The last day every figure here counts through — [today] once it has been checked out of,
     * otherwise yesterday. See `ConsistencyStats.countedThrough`.
     */
    val countedThrough: LocalDate,
    val summaries: Map<String, DailyAggregate> = emptyMap(),
    val selectedDateKey: String? = null,
    val selectedDaySessions: List<CheckInSession> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: CheckInRepository, private val timeSource: TimeSource) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val currentMonth = MutableStateFlow(YearMonth.from(timeSource.today()))
    private val selectedDateKey = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0)

    // Month summaries, re-queried when the visible month or a refresh trigger changes.
    private val monthData = combine(currentMonth, refresh) { month, _ -> month }
        .flatMapLatest { month ->
            repository.dailyAggregatesFlow(
                month.atDay(1).format(dateFormatter),
                month.atEndOfMonth().format(dateFormatter),
            ).map { month to repository.byDateKey(it) }
        }

    // The key travels **with** its rows. Read as two combine sources, a tap on a new day emits the
    // new key alongside the previous day's sessions until the query lands, so an empty day would
    // briefly be headed by its own date over another day's ledger.
    private val selectedSessions: Flow<Pair<String?, List<CheckInSession>>> =
        selectedDateKey.flatMapLatest { key ->
            if (key == null) {
                flowOf(null to emptyList())
            } else {
                repository.sessionsForDateFlow(key).map { key to it }
            }
        }

    // One day subscription drives the whole screen: the counting boundary, the today marker, and the
    // tracked-day count all roll together on refresh and at midnight, with no divergent poll loops.
    val uiState: StateFlow<HistoryUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today -> repository.trackingStartFlow().map { today to it } }
        .flatMapLatest { (today, start) ->
            // A **one-day** query answers the only all-record question left: has today been checked
            // out of yet. `countedThrough` is exactly "today's key is present", so the whole range
            // never needs reading — it used to, because the longest day, the all-time average and
            // the best streak were all ringed against on this screen, and all three are gone.
            val todayKey = today.format(dateFormatter)
            val countedThroughFlow = repository.dailyAggregatesFlow(todayKey, todayKey)
                .map { ConsistencyStats.countedThrough(repository.byDateKey(it), today) }
            combine(
                monthData,
                selectedSessions,
                countedThroughFlow,
            ) { monthPair, selection, countedThrough ->
                val (month, summaries) = monthPair
                val (selectedKey, sessions) = selection
                HistoryUiState(
                    currentMonth = month,
                    trackingStartDate = start,
                    today = today,
                    countedThrough = countedThrough,
                    summaries = summaries,
                    selectedDateKey = selectedKey,
                    selectedDaySessions = sessions,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HistoryUiState(
                currentMonth = YearMonth.from(timeSource.today()),
                // Unknown until the first query lands; the calendar reads that as "nothing recorded".
                trackingStartDate = null,
                today = timeSource.today(),
                countedThrough = timeSource.today().minusDays(1),
            ),
        )

    fun onResumed() {
        refresh.value++
    }

    fun previousMonth() {
        currentMonth.value = currentMonth.value.minusMonths(1)
        selectedDateKey.value = null
    }

    fun nextMonth() {
        currentMonth.value = currentMonth.value.plusMonths(1)
        selectedDateKey.value = null
    }

    fun selectDay(dateKey: String) {
        selectedDateKey.value = if (selectedDateKey.value == dateKey) null else dateKey
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                HistoryViewModel(container.repository, container.timeSource)
            }
        }
    }
}
