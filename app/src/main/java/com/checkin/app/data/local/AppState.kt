package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App-wide state that belongs with the record rather than in prefs: one row, [id] always [ROW_ID].
 *
 * The selected habit lives here rather than in SharedPreferences for two reasons. It is read
 * reactively by every screen, and Room gives it a flow for nothing. And it must **travel with the
 * record it indexes** — a selection restored without the habits it names would point at a row that
 * does not exist — which in `_app` it always does, backed up or not.
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
