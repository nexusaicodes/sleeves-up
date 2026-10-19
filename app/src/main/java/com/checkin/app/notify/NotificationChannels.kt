package com.checkin.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.checkin.app.R

/**
 * The single place every fixed notification channel is declared. The habits' reminder channels are
 * not fixed — one per habit, created and deleted at runtime — and live in [HabitChannels].
 *
 * Channel ids are frozen: the id is the key the OS stores the user's per-channel choices under, so
 * renaming one silently resets the importance, sound and do-not-disturb settings they had chosen.
 *
 * **Importance is frozen too, and more quietly**: the system applies it only at creation, so a
 * changed value here is ignored on any install that already has the channel. Revising it for
 * everyone means a new id, which resets the settings above. The app has an install base, so both are
 * frozen in practice: append a new id, never edit one.
 */
object NotificationChannels {

    const val TIMER = "checkin_timer_channel"
    const val REMINDER = "reminder_channel"

    /**
     * The seed habit's reminder channel — the id the single pre-habits nudge channel had, kept so an
     * updated install's channel, and every setting the user made on it, becomes that habit's. Every
     * later habit's channel is built from its uuid instead (see [HabitChannels]). The ids live on the
     * habit row, so nothing posts by this constant; it stays as the frozen record of the id, and
     * `HabitRulesTest` pins it equal to `Habit.SEED_CHANNEL_ID`, which is what the database seeds.
     * Frozen for the reasons in the object doc, like every habit's id.
     */
    const val NUDGE = "nudge_channel"

    /**
     * The group every habit's reminder channel sits in, so system settings list them together under
     * one heading rather than scattered among the timer and the session reminder. Created at runtime
     * with the habits, like the channels in it.
     */
    const val HABIT_GROUP = "habit_reminders"

    /**
     * Every id this channel has previously used, deleted rather than left orphaned in the user's
     * settings as a switch that controls nothing. Append here when retiring an id; never reuse one.
     */
    private val RETIRED_NUDGE_CHANNELS = listOf("engagement_channel", "engagement_channel_v2")

    fun ensureAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        RETIRED_NUDGE_CHANNELS.forEach(manager::deleteNotificationChannel)

        // DEFAULT rather than LOW buys placement, not noise. Below DEFAULT the shade files a
        // notification in the collapsed "Silent" group — and this one carries a deliberately stale
        // `when` (the chronometer needs it pinned to the session's start), so it sinks further the
        // longer the session runs, exactly when it most needs seeing. It stays silent regardless:
        // no channel sound, `setSilent(true)` on the spec, and heads-up needs IMPORTANCE_HIGH.
        manager.createNotificationChannel(
            NotificationChannel(
                TIMER,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setSound(null, null)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
    }
}
