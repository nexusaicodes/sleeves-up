package com.checkin.app.testing

import com.checkin.app.data.local.AppState
import com.checkin.app.data.local.AppStateDao
import com.checkin.app.data.local.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The one-row app state, seeded selecting the seed habit as the database's open callback does. */
class FakeAppStateDao : AppStateDao {
    val selected = MutableStateFlow<Long?>(Habit.SEED_ID)

    override fun selectedHabitIdFlow(): Flow<Long?> = selected

    override suspend fun selectedHabitId(): Long? = selected.value

    override suspend fun put(state: AppState) {
        selected.value = state.selectedHabitId
    }
}
