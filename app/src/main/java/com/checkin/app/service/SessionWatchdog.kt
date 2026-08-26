package com.checkin.app.service

import com.checkin.app.data.TimeSource
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.ServiceEventType
import com.checkin.app.platform.ServiceController

/**
 * Puts back whatever an open session has lost: its foreground service, its alarms, or both.
 *
 * An open session with no service is a reachable state: `START_STICKY` is best effort, and a force
 * stop, an OEM background-management kill or a crash all leave the process dead with the row still
 * open. Nothing else notices — the Check-In screen renders from the row itself, so it shows a
 * cheerfully running timer with no service behind it.
 *
 * **The service and the alarms are repaired independently, because they are lost independently.** A
 * force stop and a package replace cancel a package's alarms; a plain process kill does not. So the
 * service running says nothing about whether the day-boundary close is still standing, and the
 * alarms are ensured on every pass, before the service is even looked at — see
 * [SessionLifecycleRunner.ensureArmed] for what that repair costs to skip.
 *
 * The revive is best-effort by necessity. Starting a foreground service from the background is
 * restricted, so the call can be refused outright depending on where it is invoked from. There are
 * three callers, ordered by how likely they are to be allowed: `MainActivity.onStart` (a visible
 * Activity always is), `SessionRestoreReceiver` (`BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are
 * exempt), and `NudgeWorker`'s hourly pass (which may not be). That is why there are three and not
 * one. A refusal is logged rather than thrown, and the next caller tries again. Re-arming an alarm carries no such
 * restriction, which is the other reason it does not wait behind the service.
 */
class SessionWatchdog(
    private val repository: CheckInRepository,
    private val serviceController: ServiceController,
    private val sessionReminder: SessionLifecycleRunner,
    private val log: EngagementLog,
    private val timeSource: TimeSource,
    /** Injected so the decision is testable without a live service. */
    private val serviceRunning: () -> Boolean = { CheckInService.isRunning },
) {

    /**
     * Ensures the open session's alarms, then returns true when a service revive was *also*
     * attempted (whether or not the platform allowed it).
     *
     * Never throws, for the reason given in [SessionAlarmReceiver]: every caller is a
     * fire-and-forget `launch` on the app-wide scope. Killing the process would be a poor outcome
     * for a mechanism whose whole job is recovering from one that died.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun reviveIfNeeded(source: String): Boolean = try {
        attemptRevive(source)
    } catch (e: Exception) {
        runCatching {
            log.recordService(
                ServiceEventType.DEGRADED,
                timeSource.nowMillis(),
                "revive threw ($source): ${e.javaClass.simpleName}",
            )
        }
        false
    }

    private suspend fun attemptRevive(source: String): Boolean {
        // Alarms first, and unconditionally: a package replace clears them while leaving the row
        // open and the service perfectly able to restart itself, so gating this on the service
        // being down is exactly how a session loses its day-boundary close and keeps its timer.
        // Reports false when there is no open session, in which case it has dropped the alarms.
        if (!sessionReminder.ensureArmed(timeSource.nowMillis())) return false

        if (serviceRunning()) return false
        val active = repository.getActiveSession() ?: return false

        // revive(), not startTimer(): that path takes its timing from the intent and rewrites the
        // render mirror from it, which is right for a session that has not begun and wrong for one
        // already running.
        val started = serviceController.revive(active.id, active.startedAt)
        log.recordService(
            if (started) ServiceEventType.REVIVED else ServiceEventType.DEGRADED,
            timeSource.nowMillis(),
            if (started) source else "revive refused ($source)",
        )
        return true
    }
}
