package com.checkin.app.notify.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import kotlinx.coroutines.launch

/**
 * Receives the checkpoint alarm, runs one nudge pass, and arms the next checkpoint.
 *
 * **Re-arming here is what makes the chain self-sustaining.** Every other caller of
 * [NudgeAlarms.armNext] depends on something else happening — the app being opened, the device
 * rebooting, a deferrable worker getting a slot. This one runs off the alarm that just fired, so once
 * the first is armed the sequence continues on its own for as long as the process is allowed to exist.
 *
 * It lives in `notify/nudge/` rather than beside the session alarms because the engagement layer
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
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECKPOINT) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        // A DB read, a notification and a DB write — past what onReceive may block for.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.nudgeDispatcher.runOnce()
            } catch (_: Exception) {
                // Absorbed: the re-arm below runs regardless, so a thrown pass costs this checkpoint
                // and nothing after it. There is nowhere to report it to and nothing that would read
                // such a report.
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
