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
     * What ended this session, or null while it is still open.
     *
     * A record of what happened, not a judgement on it and not an edit: the row is immutable
     * whichever value it carries, the duration is unchanged, and nothing in the app *displays* it.
     * It exists so the CSV can say which stop instants the user chose, from which surface, and
     * which midnight chose for them — a distinction the export could not otherwise carry, since a
     * forgotten session is stamped at a plausible time.
     *
     * Nullable rather than defaulted, because an open session has not been ended by anything and a
     * value here would be a claim about a stop that has not happened. That is also why
     * [com.checkin.app.data.repository.CheckInRepository.checkOutAt] takes it with no default: the
     * boolean this replaced was inherited silently by every caller that never mentioned it.
     */
    @ColumnInfo(name = "closed_by", defaultValue = "NULL")
    val closedBy: ClosedBy? = null,
)
