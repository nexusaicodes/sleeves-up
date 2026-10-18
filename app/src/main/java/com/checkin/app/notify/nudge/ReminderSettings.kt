package com.checkin.app.notify.nudge

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The time of day each habit's check-in reminder is due, as a minute of the day (see [NudgeSchedule]),
 * over its own `reminder_prefs` namespace.
 *
 * Lives beside the rest of the nudge layer, like [NudgeSendLog], rather than among the
 * `platform/` seams: it is nudge state, and only the nudge layer and the screens that choose it
 * read it.
 *
 * **Timing, never consent.** Whether reminders are sent at all is each habit's notification
 * channel's question — see [NudgeEligibility] — so this holds when they arrive and nothing about
 * whether.
 *
 * **Keyed by the habit's uuid, never its row id.** This file is a preference the user chose, so it
 * is deliberately backed up (see `data_extraction_rules.xml`) — while `_app`, which holds the habits,
 * is not. A row id restarts on the fresh database a restore lands on, so a key built from one would
 * hand the first restored habit's time to whichever new habit took its number; a uuid can only ever
 * match the habit it was chosen for, and an orphaned one costs the default time. Its own namespace
 * rather than a key in `prompt_prefs`, because that file records prompts already raised and is
 * excluded from backup for that reason.
 *
 * **This is where the range is enforced, and the only place.** Both accessors clamp into
 * [NudgeSchedule.minuteRange], so everything downstream — the schedule maths, the rules, the copy —
 * can take the minute as valid.
 */
interface ReminderSettings {
    /** [habitUuid]'s reminder time as a minute of the day, always within [NudgeSchedule.minuteRange]. */
    fun reminderMinute(habitUuid: String): Int

    /** Stores [minute] for [habitUuid], clamped into [NudgeSchedule.minuteRange]. */
    fun setReminderMinute(habitUuid: String, minute: Int)

    /**
     * The earliest time any habit could be reminded at — every stored time and the default, which a
     * habit nobody has chosen for is reminded at. Read without the habits, for the alarm's fallback
     * when the database cannot be: it may include an archived habit's time, and waking early is the
     * harmless direction to be wrong in.
     */
    fun earliestMinute(): Int

    /**
     * Emits once on collection and again after any time changes, so a screen listing habits with
     * their times can re-read them. SharedPreferences has no flow of its own.
     */
    fun changes(): Flow<Unit>
}

class SharedPrefsReminderSettings(private val prefs: SharedPreferences) : ReminderSettings {

    companion object {
        const val NAME = "reminder_prefs"

        private const val KEY_PREFIX = "reminder_minute_"

        fun create(context: Context) = SharedPrefsReminderSettings(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    // Clamped on read as well as on write: a value from an older range, or a hand-edited file, must
    // not produce a band that runs past what the schedule allows.
    override fun reminderMinute(habitUuid: String): Int = NudgeSchedule.clampMinute(
        prefs.getInt(KEY_PREFIX + habitUuid, NudgeSchedule.DEFAULT_MINUTE),
    )

    override fun setReminderMinute(habitUuid: String, minute: Int) {
        prefs.edit { putInt(KEY_PREFIX + habitUuid, NudgeSchedule.clampMinute(minute)) }
    }

    override fun earliestMinute(): Int = prefs.all
        .filterKeys { it.startsWith(KEY_PREFIX) }
        .values
        .filterIsInstance<Int>()
        .map(NudgeSchedule::clampMinute)
        .plus(NudgeSchedule.DEFAULT_MINUTE)
        .min()

    override fun changes(): Flow<Unit> = callbackFlow {
        // Held in a local for the life of the collection: SharedPreferences keeps its listeners
        // weakly, so one created inline would be collected and stop firing.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

/**
 * Stores [minute] for [habitUuid] and asks for the checkpoint alarm to be re-armed against it,
 * returning the minute actually stored. Choosing the time already stored changes nothing and re-arms
 * nothing.
 *
 * The one path every chooser takes — the naming step and the Habits screen — so none can
 * store a new time and leave the alarm armed for the old one, which would deliver the next reminder
 * at a time the user just moved away from. [rearm] is expected to be `AppContainer.launchNudgeRearm`,
 * which runs [NudgeDispatcher.rearm] off the main thread: that call reads the day's sends, so the
 * re-arm keeps a reminder the gap is holding and arms a band the new time has just opened (put off
 * by [NudgeConfig.dueNowDelayMs], so it does not post over the screen the time was chosen on), and it
 * re-reads the times under the dispatcher's lock, so it arms for the value stored here even when a
 * checkpoint pass is running alongside.
 */
fun ReminderSettings.chooseReminderTime(habitUuid: String, minute: Int, rearm: () -> Unit): Int {
    val chosen = NudgeSchedule.clampMinute(minute)
    if (chosen == reminderMinute(habitUuid)) return chosen
    setReminderMinute(habitUuid, chosen)
    rearm()
    return reminderMinute(habitUuid)
}
