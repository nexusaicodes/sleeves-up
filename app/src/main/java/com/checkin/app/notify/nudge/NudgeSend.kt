package com.checkin.app.notify.nudge

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One nudge attempt — posted, or refused by [com.checkin.app.notify.Notifier]. The whole row,
 * because the frequency rules ask nothing else of it.
 *
 * [nudge] is the enum's name rather than its ordinal, so reordering [Nudge] cannot silently
 * reinterpret history — and rather than a [Nudge] itself, because a send of a since-retired nudge
 * still counts against the daily cap even though nothing can map it back to a constant.
 */
@Entity(tableName = "nudge_sends")
data class NudgeSend(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "nudge")
    val nudge: String,

    @ColumnInfo(name = "at")
    val at: Long,

    /**
     * Whether the post reached the tray. A refused attempt still spends its checkpoint for the day —
     * see [NudgeSnapshot.spentToday] — but not a slot in the daily cap or the gap, which count only
     * what the user was shown. The default matches [NudgeSendDatabase.MIGRATION_1_2]'s, since every
     * row written before this column existed was a post that succeeded.
     */
    @ColumnInfo(name = "posted", defaultValue = "1")
    val posted: Boolean,
)
