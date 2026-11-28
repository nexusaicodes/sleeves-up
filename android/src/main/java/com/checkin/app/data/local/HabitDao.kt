package com.checkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The habits table. There is deliberately no delete: a session points at its habit with no foreign
 * key, and that is safe only because the row it points at can never go away.
 */
@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: Habit): Long

    @Query("UPDATE habits SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE habits SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Habit?

    @Query("SELECT * FROM habits WHERE uuid = :uuid LIMIT 1")
    suspend fun byUuid(uuid: String): Habit?

    /** Oldest first, which is the order the switcher and the Habits screen list them in. */
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id ASC")
    suspend fun activeHabits(): List<Habit>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id ASC")
    fun activeHabitsFlow(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY id ASC")
    fun archivedHabitsFlow(): Flow<List<Habit>>

    /** Every habit, archived included — what the notification channels are reconciled against. */
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun allHabitsFlow(): Flow<List<Habit>>
}
