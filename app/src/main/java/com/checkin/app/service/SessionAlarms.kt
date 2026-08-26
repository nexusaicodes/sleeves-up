package com.checkin.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * The two wake-ups an open session keeps standing: the periodic "still going?" reminder, and the
 * day boundary that closes it.
 *
 * They share a seam because they share a lifetime — both are armed at check-in and both must be
 * dropped together at check-out, and splitting them across two objects would make [cancelAll] a
 * thing to remember rather than a thing to call.
 *
 * Both alarms are deliberately **inexact**. Neither instant needs to be hit precisely: the reminder
 * is a nudge on a two-hour cadence, and the day boundary stamps its check-out from the session's own
 * `date_key` rather than from when it fires, so landing late costs nothing. Exactness would cost a
 * runtime permission denied by default from Android 14, or the install-time alternative Play
 * restricts to alarm-clock apps. What is needed is the *wake*: [AlarmManager.setAndAllowWhileIdle]
 * fires through Doze, and a session forgotten overnight is exactly the case that matters.
 *
 * Deliberately **not** `setAlarmClock`: that is the only API that puts an alarm icon in the status
 * bar and an entry on the lock screen, which would tell every user the app had set them an alarm.
 *
 * This is an interface plus its Android implementation, which is the shape `platform/` holds — and
 * it lives here anyway, on purpose. The seams in `platform/` are general-purpose and say nothing
 * about sessions; this one persists a session's armed instants and its reminder count, so it is
 * inseparable from the session mechanics around it. `platform/ServiceController` is the boundary
 * that stayed general.
 */
interface SessionAlarms {
    /** Arms the next reminder at [atMillis], replacing any already set. */
    fun scheduleReminderAt(atMillis: Long)

    /** Arms the day-boundary close at [atMillis], replacing any already set. */
    fun scheduleDayBoundaryAt(atMillis: Long)

    /** Drops both alarms, both instants and the reminder count together. */
    fun cancelAll()

    /**
     * Reminders posted for the current session. Only the first alerts — a two-hour cadence that
     * buzzes every time would wake a user all night on behalf of a session they left running on
     * purpose, and the reminder has no deadline to justify that.
     */
    var remindersSent: Int

    /**
     * The instant each alarm is currently set for, or 0 when nothing is armed.
     *
     * Recorded because these alarms can be cancelled out from under an open session (see
     * [SessionLifecycleRunner.ensureArmed]) and `AlarmManager` offers no way to ask what is still
     * standing. Persisting the instants lets the repair put back what was armed rather than derive
     * fresh ones — re-deriving would push the reminder out by a full interval on every repair, and
     * would recompute the day boundary against whatever time zone the device is in now.
     */
    val nextReminderAt: Long
    val dayBoundaryAt: Long
}

class AndroidSessionAlarms(private val context: Context) : SessionAlarms {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    // The service's own namespace: this is live session mechanics, and splitting one session's state
    // across two files would only make it easier for the halves to disagree.
    private val prefs
        get() = context.getSharedPreferences(CheckInService.PREFS_NAME, Context.MODE_PRIVATE)

    // RTC_WAKEUP, not ELAPSED_REALTIME: both instants are wall-clock — one derived from the session's
    // start, one from the local calendar — and the rest of the app reasons in wall time throughout.
    override fun scheduleReminderAt(atMillis: Long) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(REMINDER))
        prefs.edit { putLong(KEY_NEXT_REMINDER_AT, atMillis) }
    }

    override fun scheduleDayBoundaryAt(atMillis: Long) {
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(DAY_BOUNDARY))
        prefs.edit { putLong(KEY_DAY_BOUNDARY_AT, atMillis) }
    }

    override fun cancelAll() {
        alarmManager?.cancel(pendingIntent(REMINDER))
        alarmManager?.cancel(pendingIntent(DAY_BOUNDARY))
        prefs.edit {
            remove(KEY_REMINDERS_SENT)
            remove(KEY_NEXT_REMINDER_AT)
            remove(KEY_DAY_BOUNDARY_AT)
        }
    }

    override var remindersSent: Int
        get() = prefs.getInt(KEY_REMINDERS_SENT, 0)
        set(value) = prefs.edit { putInt(KEY_REMINDERS_SENT, value) }

    override val nextReminderAt: Long
        get() = prefs.getLong(KEY_NEXT_REMINDER_AT, 0L)

    override val dayBoundaryAt: Long
        get() = prefs.getLong(KEY_DAY_BOUNDARY_AT, 0L)

    /**
     * One PendingIntent per action, reused, so scheduling replaces rather than accumulates and
     * [cancelAll] can find both. Request codes sit in their own band clear of every other sender's —
     * the whole allocation is listed in [com.checkin.app.notify.NotificationIds] — and the two alarms
     * take distinct codes, since equality ignores extras and a shared code would make each alarm
     * silently overwrite the other.
     */
    private fun pendingIntent(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (action == REMINDER) REQUEST_CODE_REMINDER else REQUEST_CODE_DAY_BOUNDARY,
        Intent(context, SessionAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        val REMINDER = SessionAlarmReceiver.ACTION_REMINDER
        val DAY_BOUNDARY = SessionAlarmReceiver.ACTION_DAY_BOUNDARY
        const val REQUEST_CODE_REMINDER = 20_000
        const val REQUEST_CODE_DAY_BOUNDARY = 20_001
        const val KEY_REMINDERS_SENT = "reminders_sent"
        const val KEY_NEXT_REMINDER_AT = "next_reminder_at"
        const val KEY_DAY_BOUNDARY_AT = "day_boundary_at"
    }
}
