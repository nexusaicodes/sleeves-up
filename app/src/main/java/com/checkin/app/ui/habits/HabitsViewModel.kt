package com.checkin.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.HabitRules
import com.checkin.app.data.NameProblem
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.ArchiveResult
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.HabitEdit
import com.checkin.app.data.repository.HabitRepository
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.chooseReminderHour
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An active habit with the hour its reminder is due. */
data class HabitEntry(val habit: Habit, val reminderHour: Int)

data class HabitsUiState(
    val loading: Boolean = true,
    /** Oldest first — the switcher's order, which is creation order and ranks nothing. */
    val active: List<HabitEntry> = emptyList(),
    val archived: List<Habit> = emptyList(),
    /** The habit every screen is showing. */
    val selected: Habit? = null,
    /** The habit with the open session, which locks the switcher to it; null when none is open. */
    val running: Habit? = null,
) {
    /** The first active habit still owed a name — what the first-run naming step asks about. */
    val needingName: HabitEntry? get() = active.firstOrNull { it.habit.name == null }
}

/** What a write did that the screen should say. A channel, not state, so a rotation cannot replay one. */
sealed interface HabitsEvent {
    data class Archived(val name: String?) : HabitsEvent
    data class ArchiveRefused(val result: ArchiveResult, val name: String?) : HabitsEvent
    data class RestoreRefused(val name: String?) : HabitsEvent
}

/**
 * The habits and their hours, for everything that lists or edits them: the switcher, the Habits
 * screen, the tour's habits page and the naming step.
 *
 * Every hour goes through [chooseReminderHour], and every name through [HabitRules] — checked here
 * so the dialog can say what is wrong before anything is written, and again in [HabitRepository] at
 * the write, since two dialogs can be open on one list.
 */
class HabitsViewModel(
    private val habits: HabitRepository,
    repository: CheckInRepository,
    private val reminderSettings: ReminderSettings,
    /** Launches the nudge re-arm off the main thread; see [chooseReminderHour]. */
    private val rearmNudges: () -> Unit,
) : ViewModel() {

    private val eventChannel = Channel<HabitsEvent>(Channel.BUFFERED)
    val events: Flow<HabitsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HabitsUiState> = combine(
        habits.activeHabitsFlow(),
        habits.archivedHabitsFlow(),
        habits.selectedHabitFlow(),
        repository.activeSessionFlow(),
        // Re-reads every hour when one changes: SharedPreferences has no flow of its own.
        reminderSettings.changes(),
    ) { active, archived, selected, open, _ ->
        HabitsUiState(
            loading = false,
            active = active.map { HabitEntry(it, reminderSettings.reminderHour(it.uuid)) },
            archived = archived,
            selected = selected,
            running = open?.let { session -> active.firstOrNull { it.id == session.habitId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsUiState())

    /** What is wrong with [name] for habit [forId] (null for a new one), or null when it may be saved. */
    fun nameProblem(name: String, forId: Long?): NameProblem? =
        HabitRules.nameProblem(name, uiState.value.active.map { it.habit }, forId)

    /**
     * Creates a habit when [habitId] is null, otherwise renames it, and stores [hour] for it. A new
     * habit is selected when [selectNew] — the switcher's "New habit" means "and show it" — and not
     * otherwise, so naming habits on the tour or the Habits screen leaves the screen where it was.
     */
    fun save(habitId: Long?, name: String, hour: Int, selectNew: Boolean = false) {
        viewModelScope.launch {
            val edit = if (habitId == null) habits.create(name) else habits.rename(habitId, name)
            val saved = (edit as? HabitEdit.Saved)?.habit ?: return@launch
            if (habitId == null) {
                // Stored outright and re-armed unconditionally: a new habit is a new band whatever
                // its hour, and the re-arm the insert itself triggers may have read the prefs before
                // this write landed.
                reminderSettings.setReminderHour(saved.uuid, hour)
                rearmNudges()
                if (selectNew) habits.select(saved.id)
            } else {
                reminderSettings.chooseReminderHour(saved.uuid, hour, rearmNudges)
            }
        }
    }

    fun select(habitId: Long) {
        viewModelScope.launch { habits.select(habitId) }
    }

    fun archive(habit: Habit) {
        viewModelScope.launch {
            val event = when (val result = habits.archive(habit.id)) {
                ArchiveResult.ARCHIVED -> HabitsEvent.Archived(habit.name)
                else -> HabitsEvent.ArchiveRefused(result, habit.name)
            }
            eventChannel.send(event)
        }
    }

    fun restore(habit: Habit) {
        viewModelScope.launch {
            if (habits.restore(
                    habit.id,
                ) is HabitEdit.Rejected
            ) {
                eventChannel.send(HabitsEvent.RestoreRefused(habit.name))
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                HabitsViewModel(
                    container.habitRepository,
                    container.repository,
                    container.reminderSettings,
                    container::launchNudgeRearm,
                )
            }
        }
    }
}
