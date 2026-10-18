package com.checkin.app.notify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import com.checkin.app.R
import com.checkin.app.data.local.Habit

/**
 * Each habit's reminder channel, kept in step with the habits table.
 *
 * **One channel per habit, because the channel is the opt-out.** A user who wants the gym reminder
 * and not the writing one turns one channel off; a single shared channel would make that one switch
 * for every habit, and an in-app per-habit flag beside it could only agree with the channel or lie
 * (see `NudgeEligibility`). The channel's id is stored on the habit and never changes.
 *
 * [sync] is a reconcile, not a set of operations: given every habit, it creates or renames the
 * channel of each active one and deletes the channel of each archived one. That lets one collector
 * of the habits table drive it (see `AppContainer`), so no write path can forget its channel.
 * Two platform behaviours make that safe, both read in AOSP's `PreferencesHelper` (android13 and
 * android14) rather than recalled: re-creating an existing channel updates its name and description
 * and leaves every user setting alone; and re-creating a *deleted* one undeletes the same record,
 * settings included — which is what makes restoring an archived habit bring its channel back as the
 * user left it. The tombstone is kept for 30 days and dropped after; a habit restored later than
 * that gets a fresh channel at its default importance.
 */
interface HabitChannels {
    fun sync(habits: List<Habit>)

    /** [sync] for one habit — the dispatcher's guard before it posts to [habit]'s channel. */
    fun ensure(habit: Habit) = sync(listOf(habit))
}

class AndroidHabitChannels(private val context: Context) : HabitChannels {

    override fun sync(habits: List<Habit>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(
                NotificationChannels.HABIT_GROUP,
                context.getString(R.string.habit_channel_group_name),
            ),
        )
        habits.forEach { habit ->
            if (habit.archived) {
                // Tombstoned, not removed: the record and its settings survive for the restore.
                manager.deleteNotificationChannel(habit.channelId)
            } else {
                manager.createNotificationChannel(channelFor(habit))
            }
        }
    }

    /**
     * HIGH, so it peeks. At DEFAULT a once-a-day message lands quietly in a shade the user may not
     * read for hours, which is indistinguishable from not sending it. Applied only at creation, like
     * every channel's importance, so an existing channel keeps whatever the user set.
     *
     * The group is assigned on every call, and that is how the seed habit's channel joins it: the
     * platform applies a group to an existing channel that has none, and never moves one that has.
     */
    private fun channelFor(habit: Habit): NotificationChannel {
        val name = habit.name
        return NotificationChannel(
            habit.channelId,
            name?.let { context.getString(R.string.habit_channel_name, it) }
                ?: context.getString(R.string.nudge_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            group = NotificationChannels.HABIT_GROUP
            description = name?.let { context.getString(R.string.habit_channel_description, it) }
                ?: context.getString(R.string.nudge_channel_description)
        }
    }
}
