package com.checkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * **Every query that reads a record is scoped to one habit, and the open-session ones are not.**
 * There is one open session across all habits — the switcher locks while it runs — and that rule is
 * a single query only while [getActiveSession] stays unscoped. Everything else takes a `habitId`,
 * because a habit's record is read alone: nothing in the app compares or pools two of them.
 */
@Dao
interface CheckInSessionDao {
    @Insert
    suspend fun insertSession(session: CheckInSession): Long

    /**
     * Closes [id] only while it is open; returns the rows changed, 0 when already closed or absent.
     * The guard is in the statement, so two closers racing cannot both write.
     */
    @Query(
        "UPDATE sessions SET stopped_at = :stoppedAt, duration = :duration, closed_by = :closedBy " +
            "WHERE id = :id AND stopped_at IS NULL",
    )
    suspend fun closeIfOpen(id: Long, stoppedAt: Long, duration: Long, closedBy: ClosedBy): Int

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    suspend fun getActiveSession(): CheckInSession?

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    fun getActiveSessionFlow(): Flow<CheckInSession?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): CheckInSession?

    @Query("SELECT * FROM sessions WHERE habit_id = :habitId AND date_key = :dateKey ORDER BY started_at ASC")
    suspend fun getSessionsByDate(habitId: Long, dateKey: String): List<CheckInSession>

    @Query("SELECT * FROM sessions WHERE habit_id = :habitId AND date_key = :dateKey ORDER BY started_at ASC")
    fun getSessionsByDateFlow(habitId: Long, dateKey: String): Flow<List<CheckInSession>>

    /**
     * The day summaries in a range. **This statement and [getDailyAggregatesFlow]'s are deliberately
     * identical and must stay so** — one serves the one-shot reads (the CSV export, the check-out
     * celebration), and the other serves the screens, which need to re-render on every write. Room cannot express both
     * return types off one declaration, so the SQL is duplicated; the cost is that editing one and
     * missing the other makes the file and the screen it was exported from disagree, silently.
     * Change them together.
     *
     * `stopped_at IS NOT NULL` is what makes only completed sessions aggregate: an open session
     * contributes nothing anywhere until check-out.
     *
     * The `closed_by` literals are [ClosedBy.storedValue] spellings, which an annotation cannot read
     * — an enum property is not `const`. They are frozen, so this cannot drift on its own; a *new*
     * ending means a new value there and a new sum here. `CASE WHEN` rather than the shorter
     * `SUM(closed_by = '…')` because that yields NULL, not 0, for a group where every row is null,
     * and these fields are non-null Ints.
     */
    @Query(
        """
        SELECT date_key AS dateKey,
               COALESCE(SUM(duration), 0) AS totalDurationMs,
               COUNT(*) AS sessionCount,
               MIN(started_at) AS firstCheckIn,
               MAX(stopped_at) AS lastCheckOut,
               COALESCE(SUM(CASE WHEN closed_by = 'day_boundary' THEN 1 ELSE 0 END), 0) AS dayBoundaryCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'in_app' THEN 1 ELSE 0 END), 0) AS inAppCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'timer_notification' THEN 1 ELSE 0 END), 0)
                   AS timerNotificationCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'reminder_notification' THEN 1 ELSE 0 END), 0)
                   AS reminderNotificationCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'widget' THEN 1 ELSE 0 END), 0) AS widgetCheckOuts
        FROM sessions
        WHERE habit_id = :habitId
          AND date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        GROUP BY date_key
        ORDER BY date_key ASC
    """,
    )
    suspend fun getDailyAggregates(habitId: Long, startDate: String, endDate: String): List<DailyAggregate>

    @Query(
        """
        SELECT date_key AS dateKey,
               COALESCE(SUM(duration), 0) AS totalDurationMs,
               COUNT(*) AS sessionCount,
               MIN(started_at) AS firstCheckIn,
               MAX(stopped_at) AS lastCheckOut,
               COALESCE(SUM(CASE WHEN closed_by = 'day_boundary' THEN 1 ELSE 0 END), 0) AS dayBoundaryCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'in_app' THEN 1 ELSE 0 END), 0) AS inAppCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'timer_notification' THEN 1 ELSE 0 END), 0)
                   AS timerNotificationCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'reminder_notification' THEN 1 ELSE 0 END), 0)
                   AS reminderNotificationCheckOuts,
               COALESCE(SUM(CASE WHEN closed_by = 'widget' THEN 1 ELSE 0 END), 0) AS widgetCheckOuts
        FROM sessions
        WHERE habit_id = :habitId
          AND date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        GROUP BY date_key
        ORDER BY date_key ASC
    """,
    )
    fun getDailyAggregatesFlow(habitId: Long, startDate: String, endDate: String): Flow<List<DailyAggregate>>

    /**
     * Start instants of the completed sessions in the range, for the start-time split.
     *
     * The per-day aggregates cannot serve this: `firstCheckIn` is each day's *first* session, so a
     * day worked in three blocks would contribute one start instead of three. Filtered to completed
     * sessions and keyed on `date_key` exactly as the aggregates are, so the two always describe the
     * same set of sessions.
     */
    @Query(
        """
        SELECT started_at FROM sessions
        WHERE habit_id = :habitId
          AND date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        ORDER BY started_at ASC
    """,
    )
    fun getSessionStartsFlow(habitId: Long, startDate: String, endDate: String): Flow<List<Long>>

    /**
     * The day of the habit's earliest session, or null when it has none — the day its tracking began.
     *
     * `date_key` is ISO, so its lexicographic minimum is its chronological one. Deliberately *not*
     * filtered to completed sessions: a first check-in that is still running is still the day
     * tracking began.
     */
    @Query("SELECT MIN(date_key) FROM sessions WHERE habit_id = :habitId")
    suspend fun getFirstDateKey(habitId: Long): String?

    @Query("SELECT MIN(date_key) FROM sessions WHERE habit_id = :habitId")
    fun getFirstDateKeyFlow(habitId: Long): Flow<String?>
}
