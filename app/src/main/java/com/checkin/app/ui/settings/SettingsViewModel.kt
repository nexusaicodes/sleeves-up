package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeTrigger
import com.checkin.app.notify.log.EngagementEvent
import com.checkin.app.notify.log.EngagementLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the debug cards, and nothing else — the Settings screen holds no state of its own.
 *
 * There are no in-app notification switches for it to carry: an opt-out is a notification channel,
 * so the screen's one control is a link into Android's settings, and [NotificationsCard] reads the
 * platform directly on resume rather than through here. What is left is debug-only, plus the
 * engagement event log — a real table, so it is observed reactively.
 */
class SettingsViewModel(
    private val engagementLog: EngagementLog,
    private val nudgeTrigger: NudgeTrigger,
    private val snapshotReader: DebugSnapshotReader,
) : ViewModel() {

    /**
     * Backs the debug diagnostics card — what the notification and service layers have actually
     * recorded. `WhileSubscribed`, so the query is live only while the card is open.
     */
    val recentEvents: StateFlow<List<EngagementEvent>> =
        engagementLog.recent(EVENT_LOG_LIMIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Debug harness ---

    /** The live session/service/alarm state, read fresh. See [DebugSnapshotReader]. */
    suspend fun readSnapshot(channels: List<ChannelState>): DebugSnapshot = snapshotReader.read(channels)

    /**
     * A one-shot read for the clipboard report — deliberately **not** [recentEvents]`.value`. That
     * flow is `WhileSubscribed`, so it holds its `emptyList()` seed while the log section is
     * collapsed, which is both its default and the state the report is usually copied in.
     */
    suspend fun readLog(): List<EngagementEvent> = engagementLog.recent(EVENT_LOG_LIMIT).first()

    /**
     * Sends [nudge] immediately, bypassing eligibility, so copy can be reviewed on demand.
     * [variant] overrides the install's own bucket — without it only one wording is ever reachable
     * on a given device, since bucketing is deterministic per install by design.
     */
    fun debugSend(nudge: Nudge, variant: Int) {
        viewModelScope.launch { nudgeTrigger.forceSend(nudge, variant) }
    }

    /** Runs a real evaluation pass now instead of waiting for the hourly worker. */
    fun debugRunPass() {
        viewModelScope.launch { nudgeTrigger.runOnce() }
    }

    fun debugClearLog() {
        viewModelScope.launch { engagementLog.clear() }
    }

    companion object {
        /**
         * Sized for a session's whole history, not a glance: service, alarm and nudge rows interleave,
         * and one overnight session with its two-hourly reminders fills a couple of dozen alone.
         */
        private const val EVENT_LOG_LIMIT = 100

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                SettingsViewModel(
                    container.engagementLog,
                    container.nudgeDispatcher,
                    DebugSnapshotReader(container.repository, container.sessionAlarms, container.timeSource),
                )
            }
        }
    }
}
