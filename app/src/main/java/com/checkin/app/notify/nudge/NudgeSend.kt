package com.checkin.app.notify.nudge

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One nudge send. The whole row, because the frequency rules ask nothing else of it.
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
)
