package com.checkin.app.notify.nudge

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The hour each habit's check-in reminder is due, over its own `reminder_prefs` namespace.
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
 * hand the first restored habit's hour to whichever new habit took its number; a uuid can only ever
 * match the habit it was chosen for, and an orphaned one costs a default hour. Its own namespace
 * rather than a key in `prompt_prefs`, because that file records prompts already raised and is
 * excluded from backup for that reason.
 *
 * **This is where the hour range is enforced, and the only place.** Both accessors clamp into
 * [NudgeSchedule.hourRange], so everything downstream — the schedule maths, the rules, the copy —
 * can take the hour as valid.
 */
interface ReminderSettings {
    /** [habitUuid]'s reminder hour, always within [NudgeSchedule.hourRange]. */
    fun reminderHour(habitUuid: String): Int

    /** Stores [hour] for [habitUuid], clamped into [NudgeSchedule.hourRange]. */
    fun setReminderHour(habitUuid: String, hour: Int)

    /**
     * The earliest hour any habit could be reminded at — every stored hour and the default, which a
     * habit nobody has chosen for is reminded at. Read without the habits, for the alarm's fallback
     * when the database cannot be: it may include an archived habit's hour, and waking early is the
     * harmless direction to be wrong in.
     */
    fun earliestHour(): Int

    /**
     * Emits once on collection and again after any hour changes, so a screen listing habits with
     * their hours can re-read them. SharedPreferences has no flow of its own.
     */
    fun changes(): Flow<Unit>
}

class SharedPrefsReminderSettings(private val prefs: SharedPreferences) : ReminderSettings {

    companion object {
        const val NAME = "reminder_prefs"

        private const val KEY_PREFIX = "reminder_hour_"

        fun create(context: Context) = SharedPrefsReminderSettings(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    // Clamped on read as well as on write: a value from an older range, or a hand-edited file, must
    // not produce a band that runs past what the schedule allows.
    override fun reminderHour(habitUuid: String): Int = NudgeSchedule.clampHour(
        prefs.getInt(KEY_PREFIX + habitUuid, NudgeSchedule.DEFAULT_HOUR),
    )

    override fun setReminderHour(habitUuid: String, hour: Int) {
        prefs.edit { putInt(KEY_PREFIX + habitUuid, NudgeSchedule.clampHour(hour)) }
    }

    override fun earliestHour(): Int = prefs.all
        .filterKeys { it.startsWith(KEY_PREFIX) }
        .values
        .filterIsInstance<Int>()
        .map(NudgeSchedule::clampHour)
        .plus(NudgeSchedule.DEFAULT_HOUR)
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
 * Stores [hour] for [habitUuid] and asks for the checkpoint alarm to be re-armed against it,
 * returning the hour actually stored. Choosing the hour already stored changes nothing and re-arms
 * nothing.
 *
 * The one path every chooser takes — the naming step and the Habits screen — so none can
 * store a new hour and leave the alarm armed for the old one, which would deliver the next reminder
 * at an hour the user just moved away from. [rearm] is expected to be `AppContainer.launchNudgeRearm`,
 * which runs [NudgeDispatcher.rearm] off the main thread: that call reads the day's sends, so the
 * re-arm keeps a reminder the gap is holding and arms a band the new hour has just opened (put off
 * by [NudgeConfig.dueNowDelayMs], so it does not post over the screen the hour was chosen on), and it
 * re-reads the hours under the dispatcher's lock, so it arms for the value stored here even when a
 * checkpoint pass is running alongside.
 */
fun ReminderSettings.chooseReminderHour(habitUuid: String, hour: Int, rearm: () -> Unit): Int {
    val chosen = NudgeSchedule.clampHour(hour)
    if (chosen == reminderHour(habitUuid)) return chosen
    setReminderHour(habitUuid, chosen)
    rearm()
    return reminderHour(habitUuid)
}
