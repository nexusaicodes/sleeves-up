package com.checkin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.checkin.app.CheckInApplication
import com.checkin.app.data.TimeSource
import com.checkin.app.notify.nudge.NudgeAlarms
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.chooseFirstReminderHour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(val reminderHour: Int)

/**
 * The reminder hour, and nothing else Settings shows is state. Also what the welcome tour's reminder
 * page reads and writes, so the hour has one holder and one tested write path wherever it is chosen.
 *
 * A [MutableStateFlow] seeded from the prefs rather than a Room-style `stateIn`, because
 * SharedPreferences offers no flow; this ViewModel is the only writer while the screen is up, so
 * the value it holds cannot be overtaken by a write it did not make.
 */
class SettingsViewModel(
    private val reminderSettings: ReminderSettings,
    private val nudgeAlarms: NudgeAlarms,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val state = MutableStateFlow(SettingsUiState(reminderSettings.firstReminderHour()))
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    fun setReminderHour(hour: Int) {
        val stored = reminderSettings.chooseFirstReminderHour(hour, nudgeAlarms, timeSource.nowMillis())
        state.value = SettingsUiState(stored)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as CheckInApplication).container
                SettingsViewModel(container.reminderSettings, container.nudgeAlarms, container.timeSource)
            }
        }
    }
}
