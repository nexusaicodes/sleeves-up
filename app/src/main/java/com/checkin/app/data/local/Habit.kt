package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One habit: a record of its own, kept apart from every other habit's.
 *
 * **[uuid] is the habit's permanent identity, and [id] is not.** The row id is what sessions point
 * at, and it restarts on a fresh `_app` — the destructive fallback rebuilds one — while `nudges.db`
 * and `reminder_prefs` survive. Anything keyed outside this database is therefore keyed on [uuid]:
 * the reminder time, the nudge ledger and (through [channelId]) the notification channel. A rebuilt
 * `_app` can then orphan a key, which matches nothing; it can never hand one habit's state to another.
 *
 * **Habits are never deleted**, only [archived], which is what lets `sessions.habit_id` go without a
 * foreign key: the row a session points at always exists.
 */
@Entity(tableName = "habits", indices = [Index(value = ["uuid"], unique = true)])
data class Habit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uuid")
    val uuid: String,

    /**
     * Null means the habit is **owed a name**: the record a pre-habits install carried, and the one
     * a fresh install seeds before the naming step names it. Nothing renders a habit without one — the
     * Check In button names the habit — so `FirstRun.Step.NAME_HABIT` asks for it before the app
     * opens. Stored trimmed, and unique among habits that are not archived (see [com.checkin.app.data.HabitRules]).
     */
    @ColumnInfo(name = "name")
    val name: String?,

    /** Hidden from the switcher, sends no nudges, restorable from the Habits screen. */
    @ColumnInfo(name = "archived", defaultValue = "0")
    val archived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * The notification channel this habit's reminders post to. Stored rather than derived because
     * the seed habit keeps the pre-habits channel id — `NotificationChannels.NUDGE`, which carries
     * every per-channel choice an updated install already made — while every later habit gets one
     * built from its [uuid]. Frozen once written, exactly as a channel id is.
     */
    @ColumnInfo(name = "channel_id")
    val channelId: String,
) {
    companion object {
        /**
         * The row id of the habit every install starts with. It is also `sessions.habit_id`'s
         * default, which is how the sessions recorded before habits existed belong to it.
         */
        const val SEED_ID = 1L

        /**
         * The seed habit's channel: the id the single pre-habits reminder channel already had, so
         * an updated install keeps every setting made on it. Restated here rather than imported so
         * `data/` does not reach into `notify/`; `HabitRulesTest` pins it equal to
         * `NotificationChannels.NUDGE`.
         */
        const val SEED_CHANNEL_ID = "nudge_channel"

        /** Every later habit's channel is this plus its [uuid], so no two can share one. */
        const val CHANNEL_PREFIX = "nudge_h_"
    }
}
