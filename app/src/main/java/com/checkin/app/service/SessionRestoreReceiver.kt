package com.checkin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import kotlinx.coroutines.launch

/**
 * Restores a session that was running when the process was replaced under it — by a reboot, or by
 * an app update.
 *
 * Both events end the process and take the foreground service with it, and `START_STICKY` does not
 * survive either. Both also **clear the session's alarms**, which is the more serious loss — see
 * [SessionLifecycleRunner.ensureArmed].
 *
 * `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are two of the few contexts explicitly permitted to
 * start a foreground service from the background, which is why the restore is attempted here rather
 * than left to the next app launch. The hourly [com.checkin.app.notify.nudge.NudgeWorker] pass
 * would eventually repair the alarms on its own — WorkManager reschedules itself across a package
 * replace — but it is deferrable, and an update landing at 23:00 could miss midnight entirely.
 *
 * The repair is [SessionWatchdog.reviveIfNeeded], the same call the other two callers make; this
 * receiver deliberately arms nothing itself. That call re-arms from the instants the alarms were
 * *already* set for rather than deriving fresh ones, which is what stops a reboot resetting the
 * "only the first reminder of a session alerts" ladder — resetting it alerts at full volume in the
 * small hours over a session the user left running on purpose.
 */
class SessionRestoreReceiver : BroadcastReceiver() {

    /** [SessionWatchdog.reviveIfNeeded] never throws; the `finally` below is for the re-arm. */
    override fun onReceive(context: Context, intent: Intent?) {
        // Both actions get the identical repair; the receiver only needs to know it was one of them.
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.sessionWatchdog.reviveIfNeeded()
            } finally {
                // In the `finally`, exactly as in NudgeAlarmReceiver: both actions that reach here
                // cancel the package's alarms, and on a device the user is not opening this is the
                // checkpoint's only prompt repair. A throw upstream must not take it down with it.
                runCatching { container.nudgeAlarms.armNext(container.timeSource.nowMillis()) }
                pending.finish()
            }
        }
    }
}
