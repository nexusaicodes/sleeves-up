package com.checkin.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Reads and writes the one [AppState] row. */
@Dao
interface AppStateDao {
    @Query("SELECT selected_habit_id FROM app_state WHERE id = 0")
    fun selectedHabitIdFlow(): Flow<Long?>

    @Query("SELECT selected_habit_id FROM app_state WHERE id = 0")
    suspend fun selectedHabitId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: AppState)
}
