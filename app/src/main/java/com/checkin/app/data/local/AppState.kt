package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * App-wide state that belongs with the record rather than in prefs: one row, [id] always [ROW_ID].
 *
 * The selected habit lives here rather than in SharedPreferences for two reasons. It is read
 * reactively by every screen, and Room gives it a flow for nothing. And it must be **excluded from
 * backup with the record it indexes** — a selection restored without the habits it names would point
 * at a row that does not exist — which `_app` already is.
 */
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey
    val id: Int = ROW_ID,

    @ColumnInfo(name = "selected_habit_id")
    val selectedHabitId: Long,
) {
    companion object {
        const val ROW_ID = 0
    }
}

@Dao
interface AppStateDao {
    @Query("SELECT selected_habit_id FROM app_state WHERE id = 0")
    fun selectedHabitIdFlow(): Flow<Long?>

    @Query("SELECT selected_habit_id FROM app_state WHERE id = 0")
    suspend fun selectedHabitId(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: AppState)
}
