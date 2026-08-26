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
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.engagement.EngagementReporter
import com.checkin.app.platform.ServiceController
import com.checkin.app.service.SessionLifecycleRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/** Immutable snapshot the Check-In screen renders. Elapsed time is screen-driven, not held here. */
data class CheckInUiState(
    val loading: Boolean = true,
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
    private val timeSource: TimeSource,
    private val serviceController: ServiceController,
    private val sessionReminder: SessionLifecycleRunner,
    private val engagementReporter: EngagementReporter,
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val refresh = MutableStateFlow(0)
    private val showGate = MutableStateFlow(false)
    private val presenceAction = MutableStateFlow<PresenceAction>(PresenceAction.None)

    // Rebuild on a screen resume OR when the day rolls over at midnight.
    val uiState: StateFlow<CheckInUiState> = timeSource.dayTrigger(refresh)
        .flatMapLatest { today ->
            val todayKey = today.format(dateFormatter)

            combine(
                repository.activeSessionFlow(),
                repository.sessionsForDateFlow(todayKey),
                combine(showGate, presenceAction) { show, action -> show to action },
                // "Has ever tracked" is "has ever checked in", so it is read off the sessions
                // themselves — the same reactive emission that opens the first one closes the
                // welcome, with no refresh to remember.
                repository.trackingStartFlow(),
            ) { active, sessions, gate, trackingStart ->
                // The running flag, completed total, and live-ticker basis all derive from this single
                // sessions emission (via `ticker`), so a check-out moves the closing session into the
                // total in one atomic step — no one-frame dip or 00:00:00 flash. A session still open
                // from a prior day (checked in across midnight) is not in today's list, so the ticker
                // falls back to the active row; deriving isRunning from ticker keeps it true there,
                // guarding against a double check-in while today's total correctly stays at 0.
                val openToday = sessions.firstOrNull { it.stoppedAt == null }
                val ticker = openToday ?: active?.takeIf { it.dateKey != todayKey }
                CheckInUiState(
                    loading = false,
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

    /** Called once the auth gate (face detection or biometric fallback) has passed. */
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
        viewModelScope.launch {
            val session = repository.checkIn()
            serviceController.startTimer(session.id, session.startedAt)
            // Armed here rather than inside the service, because `startTimer` can be refused — a
            // restricted standby bucket, an OEM that declines the foreground start — and a session
            // with no day-boundary close runs until the user notices, then writes a multi-day
            // duration onto a row the app gives no way to edit. Writing the row cannot be refused.
            sessionReminder.arm(session.startedAt)
            // Reported for every check-in, not just the one a notification tap opened — a nudge the
            // user acted on from inside the app is still a nudge that worked.
            engagementReporter.onCheckedIn(session.startedAt)
            // No refresh: the inserted row is what `hasEverTracked` reads, and its flow already
            // carries it.
        }
    }

    private fun executeCheckOut() {
        viewModelScope.launch {
            val active = repository.getActiveSession() ?: return@launch
            val closed = repository.checkOut(active.id)
            // Before `stop()`, and not left to it: that command is a no-op when the service has
            // already been killed, which would leave both alarms standing over a closed session.
            sessionReminder.cancel()
            serviceController.stop()
            // The other writer, MainActivity.onRootGatePassed, raises this too — a check-out from
            // the notification earned the same acknowledgement as one from the button.
            closed?.let { raiseCheckOutCelebration(repository, it, timeSource.nowMillis()) }
            // No manual refresh: the reactive session flows already reflect the closed session, and
            // the day clock owns the date roll.
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                CheckInViewModel(
                    container.repository,
                    container.timeSource,
                    container.serviceController,
                    container.sessionLifecycleRunner,
                    container.engagementReporter,
                )
            }
        }
    }
}

sealed class PresenceAction {
    data object None : PresenceAction()
    data object CheckIn : PresenceAction()
    data object CheckOut : PresenceAction()
}
