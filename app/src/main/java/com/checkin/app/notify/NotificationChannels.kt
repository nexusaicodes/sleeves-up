package com.checkin.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.checkin.app.R

/**
 * The single place every notification channel is declared.
 *
 * Channel ids are frozen: the id is the key the OS stores the user's per-channel choices under, so
 * renaming one silently resets the importance, sound and do-not-disturb settings they had chosen.
 *
 * **Importance is frozen too, and more quietly**: the system applies it only at creation, so a
 * changed value here is ignored on any install that already has the channel. Revising it for
 * everyone means a new id, which resets the settings above. Get these right before an install base.
 */
object NotificationChannels {

    const val TIMER = "checkin_timer_channel"
    const val REMINDER = "reminder_channel"

    /**
     * The check-in nudges. Named for what it is now that `notify/engagement/` is `notify/nudge/` —
     * the previous two ids are retired below.
     *
     * A channel id is user-visible state: importance is frozen at creation, so a new id is the only
     * way to change it on an install that already has the channel, and it costs every per-channel
     * choice the user had made there. Both renames were free only because this app has never had an
     * install base to charge — v1.1 and v2.0 reached Play at effectively zero installs. **That
     * exemption is now spent.** Treat this id as frozen: a fourth one costs real users their
     * settings, and the importance below can never be revised without one.
     */
    const val NUDGE = "nudge_channel"

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

        // Separate from the reminder channel on purpose: the two are silenced for different reasons,
        // and muting optional encouragement must not also mute the reminder that catches a session
        // the user forgot to close.
        //
        // HIGH, so it peeks. At DEFAULT it landed quietly in a shade the user might not read for
        // hours, which for a once-or-twice-a-day message is indistinguishable from not sending it.
        // The daily cap is what keeps that from being noise; the channel is what lets them stop it.
        manager.createNotificationChannel(
            NotificationChannel(
                NUDGE,
                context.getString(R.string.nudge_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.nudge_channel_description)
            },
        )
    }
}
