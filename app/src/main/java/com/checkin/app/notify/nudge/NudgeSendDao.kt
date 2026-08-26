package com.checkin.app.notify.nudge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Three statements, and every one of them drives behaviour. Nothing here exists to be read back
 * later by a person: a query that answered only a question nobody asks is what this table replaced.
 */
@Dao
interface NudgeSendDao {

    @Insert
    suspend fun insert(send: NudgeSend): Long

    /**
     * Every send since [since], oldest first — the whole input to the frequency rules.
     *
     * Returned as the rows rather than as a count, because the rules ask three things of the same
     * set: how many were sent (the daily cap), which ones (so a checkpoint cannot be sent twice), and
     * when the last one landed (the minimum gap). Answering all three from one query is what stops
     * them disagreeing about where the day started.
     */
    @Query("SELECT * FROM nudge_sends WHERE at >= :since ORDER BY at ASC, id ASC")
    suspend fun sentSince(since: Long): List<NudgeSend>

    @Query("DELETE FROM nudge_sends WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
