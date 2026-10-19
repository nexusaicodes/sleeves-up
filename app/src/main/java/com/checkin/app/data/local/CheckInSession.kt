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
     * Exported, never displayed — see [ClosedBy]. Nullable rather than defaulted, because an open
     * session has not been ended by anything and a value here would be a claim about a stop that
     * has not happened.
     */
    @ColumnInfo(name = "closed_by", defaultValue = "NULL")
    val closedBy: ClosedBy? = null,

    /**
     * The [Habit] this session belongs to. Its column default is [Habit.SEED_ID], and the migration's
     * `DEFAULT` says the same: that is how every session recorded before habits existed lands in
     * the habit the upgrade turned the old record into.
     *
     * **No foreign key, deliberately.** Adding one to an existing table means rebuilding it, and
     * habits are never deleted, so the row this points at always exists. Every query that reads a
     * record filters on it; the open-session queries alone do not, because there is one open session
     * across all habits and that rule is only a single query while they stay unscoped.
     *
     * Undefaulted in Kotlin, so a new insert that never mentions the habit fails to compile rather
     * than silently filing its session under the seed one.
     */
    @ColumnInfo(name = "habit_id", defaultValue = "1", index = true)
    val habitId: Long,
)
