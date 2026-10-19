package com.checkin.app.data.repository

import com.checkin.app.data.HabitRules
import com.checkin.app.data.NameProblem
import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.AppState
import com.checkin.app.data.local.AppStateDao
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.Habit
import com.checkin.app.data.local.HabitDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.UUID

/** What a name write did. */
sealed interface HabitEdit {
    data class Saved(val habit: Habit) : HabitEdit
    data class Rejected(val problem: NameProblem) : HabitEdit
}

/** What [HabitRepository.archive] did. */
enum class ArchiveResult {
    ARCHIVED,

    /** The habit has the open session; check out first. */
    RUNNING,

    /** The only active habit — the app always shows one, so it cannot be put away. */
    LAST_ACTIVE,
}

/**
 * The habits, and which one the app is showing.
 *
 * Every write here goes through [HabitRules], and the rules are re-checked against the database at
 * the write rather than trusted from the screen that asked, because two dialogs can be open on one
 * list. Nothing here touches notification channels: those are reconciled from [allHabitsFlow] by
 * `AppContainer`, so no write can forget its channel.
 */
class HabitRepository(
    private val habitDao: HabitDao,
    private val appStateDao: AppStateDao,
    private val sessionDao: CheckInSessionDao,
    private val timeSource: TimeSource = SystemTimeSource,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {

    fun activeHabitsFlow(): Flow<List<Habit>> = habitDao.activeHabitsFlow()

    fun archivedHabitsFlow(): Flow<List<Habit>> = habitDao.archivedHabitsFlow()

    fun allHabitsFlow(): Flow<List<Habit>> = habitDao.allHabitsFlow()

    suspend fun activeHabits(): List<Habit> = habitDao.activeHabits()

    suspend fun byId(id: Long): Habit? = habitDao.byId(id)

    suspend fun byUuid(uuid: String): Habit? = habitDao.byUuid(uuid)

    /**
     * The habit the app is showing — see [HabitRules.effectiveSelection] for why the open session
     * outranks the stored choice.
     *
     * **`distinctUntilChanged` is load-bearing, exactly as it is on `trackingStartFlow`.** All three Room
     * sources re-emit on any write to their table, so this would otherwise emit on every check-in and
     * check-out — and every ViewModel `flatMapLatest`s its whole graph of Room flows off it. A rebuild
     * per write is the dropped-invalidation window the `dayTrigger` dedupe exists to close. It still
     * emits on a rename, which carries a new name; screens that query by habit key on
     * [selectedHabitIdFlow] instead, so a rename redraws a label without rebuilding a query.
     *
     * A ViewModel that also reads the open session or the active habits passes its own shared copies
     * of them as [activeSession] and [activeHabits], so each table is observed once rather than once
     * per derivation.
     */
    fun selectedHabitFlow(
        activeSession: Flow<CheckInSession?> = sessionDao.getActiveSessionFlow(),
        activeHabits: Flow<List<Habit>> = habitDao.activeHabitsFlow(),
    ): Flow<Habit> = combine(
        appStateDao.selectedHabitIdFlow(),
        activeSession,
        activeHabits,
    ) { stored, active, habits ->
        HabitRules.effectiveSelection(stored, active?.habitId, habits)
    }.filterNotNull().distinctUntilChanged()

    /** [selectedHabitFlow]'s id alone — what a screen's queries rebuild on. */
    fun selectedHabitIdFlow(): Flow<Long> = selectedHabitFlow().map { it.id }.distinctUntilChanged()

    /**
     * Stores [habitId] as the selection, returning false — and storing nothing — when another
     * habit's session is open (the switcher lock) or the habit is not active. The lock is enforced
     * here as well as in the switcher, because a nudge tap selects too.
     */
    suspend fun select(habitId: Long): Boolean {
        val running = sessionDao.getActiveSession()
        if (running != null && running.habitId != habitId) return false
        if (habitDao.byId(habitId)?.archived != false) return false
        appStateDao.put(AppState(selectedHabitId = habitId))
        return true
    }

    /** Creates a habit. Its reminder time is the caller's to store, against the returned uuid. */
    suspend fun create(name: String): HabitEdit {
        HabitRules.nameProblem(name, habitDao.activeHabits())?.let { return HabitEdit.Rejected(it) }
        val uuid = newUuid()
        val habit = Habit(
            uuid = uuid,
            name = HabitRules.normalise(name),
            createdAt = timeSource.nowMillis(),
            channelId = Habit.CHANNEL_PREFIX + uuid,
        )
        return HabitEdit.Saved(habit.copy(id = habitDao.insert(habit)))
    }

    suspend fun rename(habitId: Long, name: String): HabitEdit {
        val habit = habitDao.byId(habitId) ?: return HabitEdit.Rejected(NameProblem.BLANK)
        HabitRules.nameProblem(name, habitDao.activeHabits(), forId = habitId)?.let { return HabitEdit.Rejected(it) }
        val normalised = HabitRules.normalise(name)
        habitDao.rename(habitId, normalised)
        return HabitEdit.Saved(habit.copy(name = normalised))
    }

    /**
     * Puts [habitId] away. Its sessions stay exactly as they are — archiving hides a record, it never
     * removes one — and its reminders stop because the nudge layer reads active habits only and its
     * channel is deleted. Moves the selection off it, so the stored choice never names a habit the
     * switcher does not list.
     */
    suspend fun archive(habitId: Long): ArchiveResult {
        if (sessionDao.getActiveSession()?.habitId == habitId) return ArchiveResult.RUNNING
        val active = habitDao.activeHabits()
        val remaining = active.filter { it.id != habitId }
        if (remaining.isEmpty()) return ArchiveResult.LAST_ACTIVE
        habitDao.setArchived(habitId, true)
        if (appStateDao.selectedHabitId() == habitId) appStateDao.put(AppState(selectedHabitId = remaining.first().id))
        return ArchiveResult.ARCHIVED
    }

    /**
     * Brings [habitId] back into the switcher, under the uniqueness rule an active habit is held to:
     * a name another active habit has since taken is refused rather than duplicated.
     */
    suspend fun restore(habitId: Long): HabitEdit {
        val habit = habitDao.byId(habitId) ?: return HabitEdit.Rejected(NameProblem.BLANK)
        val name = habit.name
        if (name != null) {
            HabitRules.nameProblem(name, habitDao.activeHabits(), forId = habitId)
                ?.let { return HabitEdit.Rejected(it) }
        }
        habitDao.setArchived(habitId, false)
        return HabitEdit.Saved(habit.copy(archived = false))
    }
}
