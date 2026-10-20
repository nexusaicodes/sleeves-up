package com.checkin.app.notify.nudge

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One nudge attempt for one habit — posted, or refused by [com.checkin.app.notify.Notifier]. The
 * whole row, because the frequency rules ask nothing else of it.
 *
 * [nudge] is the enum's name rather than its ordinal, so reordering [Nudge] cannot silently
 * reinterpret history — and rather than a [Nudge] itself, because a key an earlier build wrote must
 * still be readable after the constant it named is gone (see `Nudge.fromStoredKey`).
 */
@Entity(tableName = "nudge_sends", indices = [Index(value = ["habit_uuid", "at"])])
data class NudgeSend(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "nudge")
    val nudge: String,

    @ColumnInfo(name = "at")
    val at: Long,

    /**
     * Whether the post reached the tray. A refused attempt still spends its habit's day — see
     * [NudgeSnapshot.spentToday] — but does not move the gap, which spaces only what the user was
     * shown. The default matches [NudgeSendDatabase.MIGRATION_1_3]'s, since every
     * row written before this column existed was a post that succeeded.
     */
    @ColumnInfo(name = "posted", defaultValue = "1")
    val posted: Boolean,

    /**
     * The habit the nudge was about, by its permanent uuid — never its row id, which restarts on a
     * fresh `_app` while this file is backed up and could then credit one habit's send to another.
     *
     * Empty on every row written before habits existed. This database cannot know the seed habit's
     * uuid, so [NudgeDispatcher] reads an empty one as the seed habit's: those rows were all about
     * the one record such an install had, which is the record the seed habit now holds.
     */
    @ColumnInfo(name = "habit_uuid", defaultValue = "''")
    val habitUuid: String,
)
