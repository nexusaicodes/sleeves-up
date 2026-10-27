package com.checkin.app.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.TimeSource
import com.checkin.app.data.dayTrigger
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.ClosedBy
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.service.SessionActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter

/**
 * What the gate is being opened for, so that passing it performs the action the user asked for
 * rather than a fixed one. The root gate's equivalent is `PresenceCheckSignal.Reason`, which covers
 * the same actions for a check requested from a notification — with check-out split in two, one
 * value per `ClosedBy`.
 */
sealed class PresenceAction {
    data object None : PresenceAction()
    data object CheckIn : PresenceAction()
    data object CheckOut : PresenceAction()
}

/** Immutable snapshot the Check-In screen renders. Elapsed time is screen-driven, not held here. */
data class CheckInUiState(
    val loading: Boolean = true,
    /** The habit on screen, which the Check In button names. Null only until the first read lands. */
    val habitId: Long? = null,
    val habitName: String? = null,
    val isRunning: Boolean = false,
    val currentSessionStartTime: Long? = null,
    val todayDateKey: String = "",
    val todaySessions: List<CheckInSession> = emptyList(),
    val hasEverTracked: Boolean = false,
    val showPresenceGate: Boolean = false,
    val presenceAction: PresenceAction = PresenceAction.None,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModel(
    private val repository: CheckInRepository,
    private val habits: HabitRepository,
    private val timeSource: TimeSource,
    private val sessionActions: SessionActions,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val refresh = MutableStateFlow(0)
    private val showGate = MutableStateFlow(false)
    private val presenceAction = MutableStateFlow<PresenceAction>(PresenceAction.None)

    // One subscription each to the open session and the selected habit, shared by everything below:
    // the habit's id keys the rebuild and the habit itself draws the label, so the two are always
    // the same emission and the label never names a habit the queries are not about.
    private val activeSession = repository.activeSessionFlow()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)
    private val selectedHabit = habits.selectedHabitFlow(activeSession = activeSession)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    // Rebuild on a screen resume, when the day rolls over at midnight, or when the habit on screen
    // changes. Keyed on the habit's id alone — a rename redraws the label through the inner combine
    // rather than rebuilding every query — and both halves are deduped at source, so neither a resume
    // nor a write to either table re-subscribes anything.
    val uiState: StateFlow<CheckInUiState> = combine(
        timeSource.dayTrigger(refresh),
        selectedHabit.map { it.id }.distinctUntilChanged(),
    ) { today, habitId -> today to habitId }
        .flatMapLatest { (today, habitId) ->
            val todayKey = today.format(dateFormatter)

            combine(
                activeSession,
                repository.sessionsForDateFlow(habitId, todayKey),
                combine(showGate, presenceAction) { show, action -> show to action },
                // "Has ever tracked" is "has ever checked in to this habit", so it is read off the
                // sessions themselves — the same reactive emission that opens the first one closes
                // the welcome, with no refresh to remember.
                repository.trackingStartFlow(habitId),
                selectedHabit,
            ) { active, sessions, gate, trackingStart, habit ->
                // The running flag, completed total, and live-ticker basis all derive from this single
                // sessions emission (via `ticker`), so a check-out moves the closing session into the
                // total in one atomic step — no one-frame dip or flash of zero. A session still open
                // from a prior day (checked in across midnight) is not in today's list, so the ticker
                // falls back to the active row; deriving isRunning from ticker keeps it true there,
                // guarding against a double check-in while today's total correctly stays at 0. The
                // active row is unscoped — one open session across all habits — so it is kept only if
                // it is this habit's; the switcher lock means it always is, and this is the backstop.
                val openToday = sessions.firstOrNull { it.stoppedAt == null }
                val ticker = openToday ?: active?.takeIf { it.dateKey != todayKey && it.habitId == habitId }
                CheckInUiState(
                    loading = false,
                    habitId = habitId,
                    habitName = habit.name.takeIf { habit.id == habitId },
                    isRunning = ticker != null,
                    currentSessionStartTime = ticker?.startedAt,
                    todayDateKey = todayKey,
                    todaySessions = sessions,
                    hasEverTracked = trackingStart != null,
                    showPresenceGate = gate.first,
                    presenceAction = gate.second,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            // `hasEverTracked` comes from the sessions table, so it cannot be seeded synchronously.
            // The screen holds the gauge slot empty while `loading` rather than flashing the
            // first-run welcome at a user who has months of history.
            CheckInUiState(todayDateKey = timeSource.today().format(dateFormatter)),
        )

    /** Advances the date window (call on screen resume). */
    fun onResumed() {
        refresh.value++
    }

    fun requestCheckIn() {
        presenceAction.value = PresenceAction.CheckIn
        showGate.value = true
    }

    fun requestCheckOut() {
        presenceAction.value = PresenceAction.CheckOut
        showGate.value = true
    }

    fun dismissPresenceGate() {
        showGate.value = false
        presenceAction.value = PresenceAction.None
    }

    /** Called once the presence gate (face detection or its device-unlock fallback) has passed. */
    fun onAuthSuccess() {
        showGate.value = false
        when (presenceAction.value) {
            PresenceAction.CheckIn -> executeCheckIn()
            PresenceAction.CheckOut -> executeCheckOut()
            PresenceAction.None -> {}
        }
        presenceAction.value = PresenceAction.None
    }

    private fun executeCheckIn() {
        // The habit the button named when it was pressed: read off the state the user was looking at
        // rather than re-resolved after the gate, so the check-in lands on the habit they chose. The
        // live selection only when nothing has rendered yet, which no tap can precede.
        val shown = uiState.value.habitId
        sessionActions.checkIn { shown ?: habits.selectedHabitIdFlow().first() }
    }

    private fun executeCheckOut() {
        sessionActions.checkOut(ClosedBy.IN_APP)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                CheckInViewModel(
                    container.repository,
                    container.habitRepository,
                    container.timeSource,
                    container.sessionActions,
                )
            }
        }
    }
}
