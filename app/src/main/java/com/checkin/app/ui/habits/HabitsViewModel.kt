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
import com.checkin.app.notify.nudge.chooseReminderTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An active habit with the time its reminder is due. */
data class HabitEntry(val habit: Habit, val reminderMinute: Int)

data class HabitsUiState(
    val loading: Boolean = true,
    /** Oldest first — creation order, which ranks nothing. */
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
 * The habits and their times, for everything that lists or edits them: the switcher, the Habits
 * screen and the naming step.
 *
 * Every reminder time goes through [chooseReminderTime], and every name through [HabitRules] —
 * checked here so the dialog can say what is wrong before anything is written, and again in
 * [HabitRepository] at the write, since two dialogs can be open on one list.
 */
class HabitsViewModel(
    private val habits: HabitRepository,
    repository: CheckInRepository,
    private val reminderSettings: ReminderSettings,
    /** Launches the nudge re-arm off the main thread; see [chooseReminderTime]. */
    private val rearmNudges: () -> Unit,
) : ViewModel() {

    private val eventChannel = Channel<HabitsEvent>(Channel.BUFFERED)
    val events: Flow<HabitsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<HabitsUiState> = combine(
        habits.activeHabitsFlow(),
        habits.archivedHabitsFlow(),
        habits.selectedHabitFlow(),
        repository.activeSessionFlow(),
        // Re-reads every reminder time when one changes: SharedPreferences has no flow of its own.
        reminderSettings.changes(),
    ) { active, archived, selected, open, _ ->
        HabitsUiState(
            loading = false,
            active = active.map { HabitEntry(it, reminderSettings.reminderMinute(it.uuid)) },
            archived = archived,
            selected = selected,
            running = open?.let { session -> active.firstOrNull { it.id == session.habitId } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HabitsUiState())

    /** What is wrong with [name] for habit [forId] (null for a new one), or null when it may be saved. */
    fun nameProblem(name: String, forId: Long?): NameProblem? =
        HabitRules.nameProblem(name, uiState.value.active.map { it.habit }, forId)

    /**
     * Creates a habit when [habitId] is null, otherwise renames it, and stores [minute] as its
     * reminder time. A new habit is selected only when [selectNew]; otherwise the selection stays
     * where it was, so adding a habit on the Habits screen does not switch away from the current one.
     */
    fun save(habitId: Long?, name: String, minute: Int, selectNew: Boolean = false) {
        viewModelScope.launch {
            val edit = if (habitId == null) habits.create(name) else habits.rename(habitId, name)
            val saved = (edit as? HabitEdit.Saved)?.habit ?: return@launch
            if (habitId == null) {
                // Stored outright and re-armed unconditionally: a new habit is a new band whatever
                // its time, and the re-arm the insert itself triggers may have read the prefs before
                // this write landed.
                reminderSettings.setReminderMinute(saved.uuid, minute)
                rearmNudges()
                if (selectNew) habits.select(saved.id)
            } else {
                reminderSettings.chooseReminderTime(saved.uuid, minute, rearmNudges)
            }
        }
    }

    /**
     * Shows [habitId] on every tab, then calls [onSelected]. After the write rather than beside it,
     * because the usual [onSelected] leaves the Habits screen — clearing a ViewModel scoped to it,
     * and with it this scope — and a select cancelled before it lands is a tap that did nothing.
     */
    fun select(habitId: Long, onSelected: () -> Unit = {}) {
        viewModelScope.launch {
            habits.select(habitId)
            onSelected()
        }
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
