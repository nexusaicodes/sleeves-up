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
import com.checkin.app.data.repository.HabitRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
     * The last day the calendar shades — [today] once it has been checked out of, otherwise
     * yesterday. See `ConsistencyStats.countedThrough`.
     */
    val countedThrough: LocalDate,
    val summaries: Map<String, DailyAggregate> = emptyMap(),
    val selectedDateKey: String? = null,
    val selectedDaySessions: List<CheckInSession> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: CheckInRepository,
    private val habits: HabitRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val currentMonth = MutableStateFlow(YearMonth.from(timeSource.today()))
    private val selectedDateKey = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0)

    // The habit on screen. Every query below is scoped to it, and each re-subscribes only when it
    // actually changes — the flow is deduped at source, so a write to either table does not. Shared,
    // so the three pipelines below read one subscription rather than opening three.
    private val habitId = habits.selectedHabitIdFlow()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // Month summaries, re-queried when the visible month or the habit changes and never on a
    // resume. The Room flow re-emits on every write on its own, so a resume tick would only tear the
    // subscription down and rebuild it — opening the window in which an invalidation dispatched to the
    // observer being replaced is lost, which is what `dayTrigger` dedupes for the rest of the screen.
    private val monthData = combine(habitId, currentMonth) { habit, month -> habit to month }
        .flatMapLatest { (habit, month) ->
            repository.dailyAggregatesFlow(
                habit,
                month.atDay(1).format(dateFormatter),
                month.atEndOfMonth().format(dateFormatter),
            ).map { month to repository.byDateKey(it) }
        }

    // The key travels **with** its rows. Read as two combine sources, a tap on a new day emits the
    // new key alongside the previous day's sessions until the query lands, so an empty day would
    // briefly be headed by its own date over another day's ledger.
    private val selectedSessions: Flow<Pair<String?, List<CheckInSession>>> =
        combine(habitId, selectedDateKey) { habit, key -> habit to key }.flatMapLatest { (habit, key) ->
            if (key == null) {
                flowOf(null to emptyList())
            } else {
                repository.sessionsForDateFlow(habit, key).map { key to it }
            }
        }

    // One day subscription drives the whole screen: the counting boundary and the today marker roll
    // together on refresh and at midnight.
    val uiState: StateFlow<HistoryUiState> = combine(timeSource.dayTrigger(refresh), habitId) { today, habit ->
        today to habit
    }
        .flatMapLatest { (today, habit) -> repository.trackingStartFlow(habit).map { Triple(today, habit, it) } }
        .flatMapLatest { (today, habit, start) ->
            // A **one-day** query answers whether today has been checked out of yet: `countedThrough`
            // is exactly "today's key is present", so the whole record never needs reading.
            val todayKey = today.format(dateFormatter)
            val countedThroughFlow = repository.dailyAggregatesFlow(habit, todayKey, todayKey)
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
                HistoryViewModel(container.repository, container.habitRepository, container.timeSource)
            }
        }
    }
}
