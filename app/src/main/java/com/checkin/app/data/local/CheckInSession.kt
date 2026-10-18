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
     * Undefaulted in Kotlin for the reason [closedBy]'s writer is: a new insert that never mentioned
     * the habit would silently file its session under the seed one.
     */
    @ColumnInfo(name = "habit_id", defaultValue = "1", index = true)
    val habitId: Long,
)
