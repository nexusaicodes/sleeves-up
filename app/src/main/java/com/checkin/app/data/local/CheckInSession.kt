package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class CheckInSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "stopped_at")
    val stoppedAt: Long? = null,

    @ColumnInfo(name = "duration")
    val duration: Long? = null,

    @ColumnInfo(name = "date_key")
    val dateKey: String,

    /**
     * True when the day-boundary alarm closed this session rather than the user checking out.
     *
     * A record of what happened, not a judgement on it and not an edit: the row is immutable either
     * way, the duration is unchanged, and nothing in the app reads this. It exists so the CSV can
     * say which stop instants the user chose and which midnight chose for them — a distinction the
     * export could not otherwise carry, since a forgotten session is stamped at a plausible time.
     */
    @ColumnInfo(name = "auto_closed", defaultValue = "0")
    val autoClosed: Boolean = false,
)
