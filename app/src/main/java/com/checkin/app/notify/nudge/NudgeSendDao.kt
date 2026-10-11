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
     * Every attempt since [since], posted or refused, oldest first — the whole input to the frequency
     * rules.
     *
     * Returned as the rows rather than as a count, because the rules ask three things of the same
     * set: how many were shown (the daily cap), which checkpoints were tried (so one cannot be tried
     * twice), and when the last shown one landed (the minimum gap). Answering all three from one
     * query is what stops them disagreeing about where the day started.
     */
    @Query("SELECT * FROM nudge_sends WHERE at >= :since ORDER BY at ASC, id ASC")
    suspend fun attemptsSince(since: Long): List<NudgeSend>

    @Query("DELETE FROM nudge_sends WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
