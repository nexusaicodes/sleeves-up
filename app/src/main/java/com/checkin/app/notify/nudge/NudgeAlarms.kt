package com.checkin.app.notify.nudge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId

/**
 * The single wake-up that carries the check-in nudge: one alarm, set for the instant
 * [NudgeEligibility.nextEligibleAt] names.
 *
 * **This exists because a periodic worker could not do the job.** [NudgeWorker] asks its question at
 * whatever moment it happens to run, and an app the user is not opening drops through Android's
 * standby buckets until that is roughly once a day — at a *consistent* hour, since a deferred
 * periodic job settles into one. A nudge whose whole trigger is "is it past 10am" then never fires
 * again, and the failure is self-reinforcing: the user this feature exists for is precisely the one
 * whose worker has stopped running. A time-of-day trigger needs a time-of-day primitive.
 *
 * Inexact, for the reasons set out on [com.checkin.app.service.SessionAlarms] — and with more room
 * to spare here, since encouragement has no deadline at all.
 *
 * **Deliberately stateless, unlike [com.checkin.app.service.SessionAlarms].** That seam persists its
 * armed instants because they are derived from a session and cannot be recomputed without it. The
 * next nudge instant is derivable from now, the chosen hour and the day's sends, so there is nothing
 * to store and nothing to reconcile against `AlarmManager` (which cannot be asked what is still set).
 *
 * **This seam only sets an instant; [NudgeDispatcher] decides it.** The alarm is one `PendingIntent`,
 * so whatever arms last wins, and a caller arming a plain checkpoint would replace a retry the gap
 * was holding or skip a band that is open but unsent. Arming sites therefore go through
 * [NudgeDispatcher] — via `AppContainer.runNudgePass` and `AppContainer.rearmNudges`, which fall back
 * to [armNextCheckpoint] when the dispatcher cannot be built — never through [arm] directly.
 */
interface NudgeAlarms {
    /** Arms the checkpoint alarm for [atMs], replacing whatever was set. A past instant fires at once. */
    fun arm(atMs: Long)
}

/** The plain next checkpoint: what to arm when the day's sends cannot be read. */
fun NudgeAlarms.armNextCheckpoint(nowMs: Long, zone: ZoneId, firstHour: Int) =
    arm(NudgeSchedule.nextCheckpointAfter(nowMs, zone, firstHour))

class AndroidNudgeAlarms(private val context: Context) : NudgeAlarms {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    // RTC_WAKEUP, not ELAPSED_REALTIME: a checkpoint is a local wall-clock hour.
    override fun arm(atMs: Long) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pendingIntent())
    }

    /**
     * One PendingIntent, rebuilt identically each time so arming replaces rather than accumulates.
     * The request code sits clear of every other sender's band — the whole allocation is listed in
     * [com.checkin.app.notify.NotificationIds] — because all of them share one process-wide
     * namespace and equality ignores extras, so a collision would make one silently overwrite
     * another.
     */
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_CHECKPOINT,
        Intent(context, NudgeAlarmReceiver::class.java).setAction(NudgeAlarmReceiver.ACTION_CHECKPOINT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE_CHECKPOINT = 20_010
    }
}
