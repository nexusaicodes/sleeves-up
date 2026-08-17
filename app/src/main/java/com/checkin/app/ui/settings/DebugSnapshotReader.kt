package com.checkin.app.ui.settings

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.engagement.NudgeAlarms
import com.checkin.app.service.CheckInService
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionSchedule
import java.time.ZoneId

/**
 * Gathers the live state a [DebugSnapshot] describes.
 *
 * Its own object rather than four more parameters on [SettingsViewModel]: none of this is settings,
 * and the VM would otherwise be the only place holding the repository, the alarms and the service
 * flag together — reading as a dependency of the screen rather than the one-off inspection it is.
 *
 * Read fresh each call. Nothing it touches is reactive (prefs, and a static on the service), so a
 * cached snapshot would be exactly the stale picture the card exists to avoid.
 */
class DebugSnapshotReader(
    private val repository: CheckInRepository,
    private val sessionAlarms: SessionAlarms,
    private val nudgeAlarms: NudgeAlarms,
    private val timeSource: TimeSource,
    /** Injected for the same reason [com.checkin.app.service.SessionWatchdog] injects it: a live service is not testable. */
    private val serviceRunning: () -> Boolean = { CheckInService.isRunning },
) {

    /**
     * [channels] comes from the caller, as [com.checkin.app.notify.NotificationDelivery] takes its
     * switches: those reads need a `Context`, and this class stays reachable from the JVM suite.
     */
    suspend fun read(channels: List<ChannelState>): DebugSnapshot {
        val session = repository.getActiveSession()
        return DebugSnapshot(
            nowMs = timeSource.nowMillis(),
            session = session?.let { SessionState(it.id, it.startedAt, it.dateKey) },
            serviceRunning = serviceRunning(),
            nextReminderAt = sessionAlarms.nextReminderAt,
            dayBoundaryAt = sessionAlarms.dayBoundaryAt,
            remindersSent = sessionAlarms.remindersSent,
            expectedDayBoundaryAt = session?.let {
                SessionSchedule.dayBoundaryOf(it.dateKey, ZoneId.systemDefault())
            },
            nextCheckpointAt = nudgeAlarms.nextCheckpointAt,
            channels = channels,
        )
    }
}
