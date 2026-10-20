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
 * *already* set for rather than deriving fresh ones, so a reboot does not reset the reminder count
 * and alert at full volume in the small hours over a session left running on purpose. The nudge
 * checkpoint, which both events also cancel, is re-armed by `CheckInApplication.onCreate`, which
 * runs in the process this broadcast starts.
 */
class SessionRestoreReceiver : BroadcastReceiver() {

    /** [SessionWatchdog.reviveIfNeeded] never throws; the `finally` releases the broadcast regardless. */
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.sessionWatchdog.reviveIfNeeded()
            } finally {
                pending.finish()
            }
        }
    }
}
