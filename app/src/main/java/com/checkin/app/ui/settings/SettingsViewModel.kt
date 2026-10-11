package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.chooseFirstReminderHour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(val reminderHour: Int)

/**
 * The reminder hour, and nothing else Settings shows is state. The welcome tour chooses the hour
 * too, through the same [chooseFirstReminderHour], so the store-then-re-arm has one tested path
 * wherever the hour is chosen.
 *
 * A [MutableStateFlow] seeded from the prefs rather than a Room-style `stateIn`, because
 * SharedPreferences offers no flow; this ViewModel is the only writer while the screen is up, so
 * the value it holds cannot be overtaken by a write it did not make.
 */
class SettingsViewModel(
    private val reminderSettings: ReminderSettings,
    /** Launches the nudge re-arm off the main thread; see [chooseFirstReminderHour]. */
    private val rearmNudges: () -> Unit,
) : ViewModel() {

    private val state = MutableStateFlow(SettingsUiState(reminderSettings.firstReminderHour()))
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    fun setReminderHour(hour: Int) {
        val stored = reminderSettings.chooseFirstReminderHour(hour, rearmNudges)
        state.value = SettingsUiState(stored)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                SettingsViewModel(container.reminderSettings, container::launchNudgeRearm)
            }
        }
    }
}
