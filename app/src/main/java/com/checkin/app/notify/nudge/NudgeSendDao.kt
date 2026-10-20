package com.checkin.app.notify.nudge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Three statements, and every one of them drives behaviour. Nothing here exists to be read back
 * later by a person — see [NudgeSendLog].
 */
@Dao
interface NudgeSendDao {

    @Insert
    suspend fun insert(send: NudgeSend): Long

    /**
     * Every attempt since [since], posted or refused, oldest first — the whole input to the frequency
     * rules.
     *
     * Returned as the rows rather than as a count, and unfiltered by habit, because the rules ask two
     * things of the same set: which habits were already tried (so none is tried twice), and when the
     * last shown one landed, whichever habit it was (the minimum gap). Answering both from one query
     * is what stops them disagreeing about where the day started; the split by habit is done in
     * Kotlin.
     */
    @Query("SELECT * FROM nudge_sends WHERE at >= :since ORDER BY at ASC, id ASC")
    suspend fun attemptsSince(since: Long): List<NudgeSend>

    @Query("DELETE FROM nudge_sends WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}
