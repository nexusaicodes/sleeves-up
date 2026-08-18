package com.checkin.app.notify.engagement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.launch

/**
 * Receives the checkpoint alarm, runs one nudge pass, and arms the next checkpoint.
 *
 * **Re-arming here is what makes the chain self-sustaining.** Every other caller of
 * [NudgeAlarms.armNext] depends on something else happening — the app being opened, the device
 * rebooting, a deferrable worker getting a slot. This one runs off the alarm that just fired, so once
 * the first is armed the sequence continues on its own for as long as the process is allowed to exist.
 *
 * It lives in `notify/engagement/` rather than beside the session alarms because the engagement layer
 * is structurally isolated: it may read tracking state to build a snapshot, but it writes nothing to
 * `sessions` and nothing in `service/` depends on it.
 */
class NudgeAlarmReceiver : BroadcastReceiver() {

    /**
     * The `catch` is load-bearing, exactly as in [com.checkin.app.service.SessionAlarmReceiver] —
     * and note that a throw here would take down any running session's notification, which has
     * nothing to do with nudges.
     *
     * The re-arm sits in the `finally`, so a pass that throws still schedules the next checkpoint.
     * Without that, one bad pass would end the chain permanently and reproduce the silence this whole
     * mechanism exists to fix.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECKPOINT) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        // A DB read, a notification and a DB write — past what onReceive may block for.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.nudgeDispatcher.runOnce()
                container.engagementLog.recordService(
                    ServiceEventType.CHECKPOINT_FIRED,
                    container.timeSource.nowMillis(),
                    "nudge checkpoint",
                )
            } catch (e: Exception) {
                // Best-effort breadcrumb; the log write is exactly what may have thrown.
                runCatching {
                    container.engagementLog.recordService(
                        ServiceEventType.DEGRADED,
                        container.timeSource.nowMillis(),
                        "checkpoint threw: ${e.javaClass.simpleName}",
                    )
                }
            } finally {
                runCatching { container.nudgeAlarms.armNext(container.timeSource.nowMillis()) }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECKPOINT = "com.checkin.app.NUDGE_CHECKPOINT_DUE"
    }
}
