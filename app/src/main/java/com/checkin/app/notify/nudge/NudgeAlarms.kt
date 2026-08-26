package com.checkin.app.notify.nudge

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId

/**
 * The single wake-up that carries the check-in nudge: one alarm, always set for the next
 * [NudgeSchedule.Checkpoint].
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
 * armed instants because they are derived from a session and cannot be recomputed without it. A
 * checkpoint is pure clock maths, so the next one is always derivable from now — there is nothing to
 * store, nothing to reconcile against `AlarmManager` (which cannot be asked what is still set), no
 * new prefs namespace, and no new backup exclusion. [armNext] is idempotent: call it as often as
 * anything remembers to.
 */
interface NudgeAlarms {
    /** Arms the next checkpoint strictly after [nowMs], replacing whatever was set. */
    fun armNext(nowMs: Long)
}

class AndroidNudgeAlarms(private val context: Context) : NudgeAlarms {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    // RTC_WAKEUP, not ELAPSED_REALTIME: a checkpoint is a local wall-clock hour.
    override fun armNext(nowMs: Long) {
        val manager = alarmManager ?: return
        val at = NudgeSchedule.nextCheckpointAfter(nowMs, ZoneId.systemDefault())
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent())
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
