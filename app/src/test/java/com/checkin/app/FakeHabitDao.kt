package com.checkin.app

import com.checkin.app.data.local.AppState
import com.checkin.app.data.local.AppStateDao
import com.checkin.app.data.local.Habit
import com.checkin.app.data.local.HabitDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory habits table, seeded the way every real install starts: with the seed habit — the shape
 * `AppDatabase.seedIfEmpty` gives a fresh install and an upgraded one alike. Named by default, since
 * most tests are about a habit past the naming step; pass a null [seedName] for one still owed it.
 */
class FakeHabitDao(seedName: String? = "Seed") : HabitDao {
    private val store = MutableStateFlow(
        listOf(
            Habit(
                id = Habit.SEED_ID,
                uuid = SEED_UUID,
                name = seedName,
                createdAt = 0L,
                channelId = Habit.SEED_CHANNEL_ID,
            ),
        ),
    )
    val habits: List<Habit> get() = store.value
    private var nextId = Habit.SEED_ID + 1

    override suspend fun insert(habit: Habit): Long {
        val stored = habit.copy(id = nextId++)
        store.value = store.value + stored
        return stored.id
    }

    override suspend fun rename(id: Long, name: String) {
        store.value = store.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        store.value = store.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    override suspend fun byId(id: Long): Habit? = store.value.firstOrNull { it.id == id }

    override suspend fun byUuid(uuid: String): Habit? = store.value.firstOrNull { it.uuid == uuid }

    override suspend fun activeHabits(): List<Habit> = store.value.filter { !it.archived }

    override fun activeHabitsFlow(): Flow<List<Habit>> = store.map { list -> list.filter { !it.archived } }

    override fun archivedHabitsFlow(): Flow<List<Habit>> = store.map { list -> list.filter { it.archived } }

    override fun allHabitsFlow(): Flow<List<Habit>> = store

    companion object {
        const val SEED_UUID = "seed-uuid"
    }
}

/** The one-row app state, seeded selecting the seed habit as the database's open callback does. */
class FakeAppStateDao : AppStateDao {
    val selected = MutableStateFlow<Long?>(Habit.SEED_ID)

    override fun selectedHabitIdFlow(): Flow<Long?> = selected

    override suspend fun selectedHabitId(): Long? = selected.value

    override suspend fun put(state: AppState) {
        selected.value = state.selectedHabitId
    }
}
