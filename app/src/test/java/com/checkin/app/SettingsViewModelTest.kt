package com.checkin.app

import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.chooseFirstReminderHour
import com.checkin.app.ui.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Choosing the reminder hour is two writes — the stored hour and the armed alarm — and a chooser
 * that made only the first would deliver the next reminder at the hour the user just moved away
 * from. Both choosers go through [chooseFirstReminderHour], so it is pinned here alongside the one
 * of them a JVM suite can reach.
 */
class SettingsViewModelTest {

    private var rearms = 0
    private val rearm: () -> Unit = { rearms++ }

    @Test
    fun `opens on the stored hour`() {
        val vm = SettingsViewModel(FakeReminderSettings(7), rearm)

        assertEquals(7, vm.uiState.value.reminderHour)
    }

    @Test
    fun `a new hour is stored, re-armed and shown`() {
        val settings = FakeReminderSettings()
        val vm = SettingsViewModel(settings, rearm)

        vm.setReminderHour(15)

        assertEquals(15, settings.firstReminderHour())
        assertEquals(1, rearms)
        assertEquals(15, vm.uiState.value.reminderHour)
    }

    /** The screen shows what was stored, not what was asked for, so the two cannot disagree. */
    @Test
    fun `an out-of-range hour shows as the clamped one`() {
        val vm = SettingsViewModel(FakeReminderSettings(), rearm)

        vm.setReminderHour(23)

        assertEquals(NudgeSchedule.firstHourRange.last, vm.uiState.value.reminderHour)
    }

    /**
     * Re-choosing the stored hour — tapping the row already selected — must not re-arm: nothing
     * changed, and the re-arm is a database read and a binder call for no new answer.
     */
    @Test
    fun `choosing the stored hour again re-arms nothing`() {
        val settings = FakeReminderSettings(12)

        assertEquals(12, settings.chooseFirstReminderHour(12, rearm))
        // Clamps to the stored hour too, so it is the same choice.
        FakeReminderSettings(NudgeSchedule.firstHourRange.last).chooseFirstReminderHour(23, rearm)
        assertEquals(0, rearms)
    }
}
