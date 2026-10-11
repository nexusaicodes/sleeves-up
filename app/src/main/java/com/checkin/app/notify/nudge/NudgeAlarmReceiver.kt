package com.checkin.app.notify.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import kotlinx.coroutines.launch

/**
 * Receives the checkpoint alarm and runs one nudge pass, which arms the next one.
 *
 * **The pass this runs is what makes the chain self-sustaining.** Every other arming site depends on
 * something else happening — the app being opened, the device rebooting, a deferrable worker getting
 * a slot. This one runs off the alarm that just fired, and [NudgeDispatcher.runOnce] arms the next
 * before it returns, so once the first is armed the sequence continues on its own for as long as the
 * process is allowed to exist.
 *
 * It lives in `notify/nudge/` rather than beside the session alarms because `notify/nudge/` is
 * structurally isolated: it may read tracking state to build a snapshot, but it writes nothing to
 * `sessions` and nothing in `service/` depends on it.
 */
class NudgeAlarmReceiver : BroadcastReceiver() {

    /**
     * Goes through `AppContainer.runNudgePass` rather than the dispatcher directly, and that is
     * load-bearing, exactly as the `catch` in [com.checkin.app.service.SessionAlarmReceiver] is — a
     * throw here would take down any running session's notification, which has nothing to do with
     * nudges. [NudgeDispatcher.runOnce] arms on every path it reaches, a throwing pass included, so
     * what can still throw is building the dispatcher itself, and `runNudgePass` arms the plain
     * checkpoint without it: the chain must not end because one pass could not start, or this
     * reproduces the silence the whole mechanism exists to fix. `finish` sits in the `finally`, so
     * the broadcast is released whatever happened above it.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECKPOINT) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        // A DB read, a notification and a DB write — past what onReceive may block for.
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.runNudgePass()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECKPOINT = "com.checkin.app.NUDGE_CHECKPOINT_DUE"
    }
}
