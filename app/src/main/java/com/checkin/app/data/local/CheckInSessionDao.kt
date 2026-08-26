package com.checkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInSessionDao {
    @Insert
    suspend fun insertSession(session: CheckInSession): Long

    @Update
    suspend fun updateSession(session: CheckInSession)

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    suspend fun getActiveSession(): CheckInSession?

    @Query("SELECT * FROM sessions WHERE stopped_at IS NULL LIMIT 1")
    fun getActiveSessionFlow(): Flow<CheckInSession?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): CheckInSession?

    @Query("SELECT * FROM sessions WHERE date_key = :dateKey ORDER BY started_at ASC")
    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession>

    @Query("SELECT * FROM sessions WHERE date_key = :dateKey ORDER BY started_at ASC")
    fun getSessionsByDateFlow(dateKey: String): Flow<List<CheckInSession>>

    /**
     * The day summaries in a range. **This statement and [getDailyAggregatesFlow]'s are deliberately
     * identical and must stay so** — one serves the CSV export, which needs an answer once, and the
     * other serves the screens, which need to re-render on every write. Room cannot express both
     * return types off one declaration, so the SQL is duplicated; the cost is that editing one and
     * missing the other makes the file and the screen it was exported from disagree, silently.
     * Change them together.
     *
     * `stopped_at IS NOT NULL` is what makes only completed sessions aggregate: an open session
     * contributes nothing anywhere until check-out.
     */
    @Query(
        """
        SELECT date_key AS dateKey,
               COALESCE(SUM(duration), 0) AS totalDurationMs,
               COUNT(*) AS sessionCount,
               MIN(started_at) AS firstCheckIn,
               MAX(stopped_at) AS lastCheckOut,
               COALESCE(SUM(auto_closed), 0) AS autoClosedSessions
        FROM sessions
        WHERE date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        GROUP BY date_key
        ORDER BY date_key ASC
    """,
    )
    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate>

    @Query(
        """
        SELECT date_key AS dateKey,
               COALESCE(SUM(duration), 0) AS totalDurationMs,
               COUNT(*) AS sessionCount,
               MIN(started_at) AS firstCheckIn,
               MAX(stopped_at) AS lastCheckOut,
               COALESCE(SUM(auto_closed), 0) AS autoClosedSessions
        FROM sessions
        WHERE date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        GROUP BY date_key
        ORDER BY date_key ASC
    """,
    )
    fun getDailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>>

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
        WHERE date_key BETWEEN :startDate AND :endDate
          AND stopped_at IS NOT NULL
        ORDER BY started_at ASC
    """,
    )
    fun getSessionStartsFlow(startDate: String, endDate: String): Flow<List<Long>>

    /**
     * The day of the earliest session, or null when there are none — the day tracking began.
     *
     * `date_key` is ISO, so its lexicographic minimum is its chronological one. Deliberately *not*
     * filtered to completed sessions: a first check-in that is still running is still the day
     * tracking began.
     */
    @Query("SELECT MIN(date_key) FROM sessions")
    suspend fun getFirstDateKey(): String?

    @Query("SELECT MIN(date_key) FROM sessions")
    fun getFirstDateKeyFlow(): Flow<String?>
}
