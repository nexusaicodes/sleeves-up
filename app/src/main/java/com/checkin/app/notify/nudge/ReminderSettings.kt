package com.checkin.app.notify.nudge

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * The hour the user chose for the first check-in reminder, over its own `reminder_prefs` namespace.
 *
 * Lives beside the rest of the nudge layer, like [NudgeSendLog], rather than among the
 * `platform/` seams: it is nudge state, and only the nudge layer and its two choosers read it.
 *
 * **Timing, never consent.** Whether reminders are sent at all is the notification channel's
 * question — see [NudgeEligibility] — so this holds when they arrive and nothing about whether.
 *
 * Its own namespace rather than a key in `prompt_prefs`, because that file records prompts already
 * raised and is excluded from cloud backup for that reason. This is a preference: restored onto a
 * new device it lands the user's own choice there, which is the right outcome, so it is
 * deliberately **not** excluded (see `data_extraction_rules.xml`).
 *
 * **This is where the hour range is enforced, and the only place.** Both accessors clamp into
 * [NudgeSchedule.firstHourRange], so everything downstream — the schedule maths, the rules, the
 * copy — can take the hour as valid.
 */
interface ReminderSettings {
    /** The first reminder's local hour, always within [NudgeSchedule.firstHourRange]. */
    fun firstReminderHour(): Int

    /** Stores [hour], clamped into [NudgeSchedule.firstHourRange]. */
    fun setFirstReminderHour(hour: Int)
}

class SharedPrefsReminderSettings(private val prefs: SharedPreferences) : ReminderSettings {

    companion object {
        const val NAME = "reminder_prefs"

        private const val KEY_FIRST_REMINDER_HOUR = "first_reminder_hour"

        fun create(context: Context) = SharedPrefsReminderSettings(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    // Clamped on read as well as on write: a value from an older range, or a hand-edited file, must
    // not produce a follow-up band that runs past midnight.
    override fun firstReminderHour(): Int = NudgeSchedule.clampFirstHour(
        prefs.getInt(KEY_FIRST_REMINDER_HOUR, NudgeSchedule.DEFAULT_FIRST_HOUR),
    )

    override fun setFirstReminderHour(hour: Int) {
        prefs.edit { putInt(KEY_FIRST_REMINDER_HOUR, NudgeSchedule.clampFirstHour(hour)) }
    }
}

/**
 * Stores [hour] and re-arms the checkpoint alarm against it, returning the hour actually stored.
 *
 * The one path both choosers take — the tour and Settings — so neither can store a new hour and
 * leave the alarm armed for the old one, which would deliver the next reminder at an hour the user
 * just moved away from. The arm is guarded like every other arming site: a throw past the
 * platform's alarm cap must not take the screen down, and the next process start re-arms anyway.
 *
 * It arms the plain next checkpoint, without [NudgeDispatcher.rearm]'s gap retry: the choosers run on
 * the main thread, the retry needs a ledger read, and a gap-deferred nudge was computed against the
 * hour the user has just replaced.
 */
fun ReminderSettings.chooseFirstReminderHour(hour: Int, alarms: NudgeAlarms, nowMs: Long): Int {
    setFirstReminderHour(hour)
    runCatching { alarms.armNext(nowMs) }
    return firstReminderHour()
}
