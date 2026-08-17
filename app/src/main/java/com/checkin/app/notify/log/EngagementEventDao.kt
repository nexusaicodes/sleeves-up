package com.checkin.app.notify.log

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EngagementEventDao {

    @Insert
    suspend fun insert(event: EngagementEvent): Long

    /** Unscoped on purpose: the debug harness shows every notification the app sent, of either kind. */
    @Query("SELECT * FROM engagement_events ORDER BY at DESC, id DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EngagementEvent>>

    // The two queries below drive the nudge frequency cap and conversion attribution, so both are
    // scoped to `source`. Widened to every row, a session reminder would count against the daily
    // cap and would take the credit for a tap or a check-in that a nudge earned.

    @Query(
        "SELECT * FROM engagement_events WHERE event = :event AND source = :source AND at >= :since " +
            "ORDER BY at DESC, id DESC LIMIT 1",
    )
    suspend fun latestOfType(event: String, source: String, since: Long): EngagementEvent?

    /**
     * Every matching row rather than a count, because the frequency rules ask three questions of the
     * same set — how many were sent, which ones, and when the last landed — and three queries is how
     * those answers come to disagree about where the day started.
     */
    @Query(
        "SELECT * FROM engagement_events WHERE event = :event AND source = :source AND at >= :since " +
            "ORDER BY at ASC, id ASC",
    )
    suspend fun ofTypeSince(event: String, source: String, since: Long): List<EngagementEvent>

    @Query("DELETE FROM engagement_events WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM engagement_events")
    suspend fun clear()
}
