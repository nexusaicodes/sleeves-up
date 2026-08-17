package com.checkin.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.log.ServiceEventType
import kotlinx.coroutines.launch

/**
 * Restores a session that was running when the process was replaced under it — by a reboot, or by
 * an app update.
 *
 * Both events end the process and take the foreground service with it, and `START_STICKY` does not
 * survive either. Both also **clear the session's alarms**, which is the more serious loss: the
 * day-boundary close is the only thing that ends a session the user has forgotten, and without it a
 * session runs until they notice and then writes a multi-day duration onto a row nothing can edit.
 *
 * `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` are two of the few contexts explicitly permitted to
 * start a foreground service from the background, which is why the restore is attempted here rather
 * than left to the next app launch. The hourly [com.checkin.app.notify.engagement.NudgeWorker] pass
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

    /** See [SessionAlarmReceiver] for why the `catch` is required rather than tidy. */
    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent?) {
        // Named for the diagnostics card, which shows this detail verbatim — a fully qualified
        // action string would push everything after it off the row.
        val source = when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "update"
            else -> return
        }
        val app = context.applicationContext as? CheckInApplication ?: return
        val container = app.container

        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.sessionWatchdog.reviveIfNeeded(source)
                // Both actions that reach here cancel the package's alarms, and the checkpoint alarm
                // has no other repair path on a device the user is not opening the app on.
                container.nudgeAlarms.armNext(container.timeSource.nowMillis())
            } catch (e: Exception) {
                runCatching {
                    container.engagementLog.recordService(
                        ServiceEventType.DEGRADED,
                        container.timeSource.nowMillis(),
                        "restore threw: ${e.javaClass.simpleName}",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }
}
