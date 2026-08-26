package com.checkin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import kotlinx.coroutines.launch

/**
 * Receives both session alarms — the periodic reminder and the day-boundary close — and hands each
 * to [SessionLifecycleRunner].
 *
 * Deliberately does its work here rather than delegating to [CheckInService]: the alarms' whole
 * purpose is to be reliable when the service is not, and a broadcast receiver can run in a process
 * the broadcast itself just created — whereas starting a foreground service from the background is
 * restricted and would be refused in exactly that case. Posting a notification and closing a row
 * need neither a service nor a foreground state.
 *
 * The service is told afterwards only so its notification stops advancing or is taken down, and only
 * when it is already alive in this process. If it is not, there is no notification to correct.
 */
class SessionAlarmReceiver : BroadcastReceiver() {

    /**
     * The `catch` is load-bearing. `applicationScope` carries a `SupervisorJob` but **no**
     * `CoroutineExceptionHandler`, so an uncaught throw in a root `launch` reaches the default
     * handler and kills the process — taking the running session's notification with it, which is
     * the exact failure these alarms exist to prevent. A DB read on a corrupt sessions database or
     * a refused `setAndAllowWhileIdle` are both realistic sources. There is nowhere to report the
     * swallowed failure to; the alarm chain re-arms itself regardless.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != ACTION_REMINDER && action != ACTION_DAY_BOUNDARY) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        // The work is a DB read, a notification and a DB write — past what onReceive may block for.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val runner = container.sessionLifecycleRunner
                val outcome = if (action == ACTION_DAY_BOUNDARY) {
                    runner.onDayBoundaryFired()
                } else {
                    runner.onReminderFired()
                }
                // Only a live service has a notification to take down, and only a live service can
                // be sent a command from here without tripping the background-start restriction.
                if (CheckInService.isRunning) {
                    if (outcome is SessionLifecycleRunner.Outcome.Closed) {
                        container.serviceController.stop()
                    } else {
                        container.serviceController.refreshFromDb()
                    }
                }
            } catch (_: Exception) {
                // Absorbed on purpose: see the class KDoc. The next alarm in the chain is already
                // armed, so a thrown pass costs this delivery and nothing beyond it.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.checkin.app.SESSION_REMINDER_DUE"
        const val ACTION_DAY_BOUNDARY = "com.checkin.app.SESSION_DAY_BOUNDARY"
    }
}
