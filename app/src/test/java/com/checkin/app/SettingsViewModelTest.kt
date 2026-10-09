package com.checkin.app

import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.chooseFirstReminderHour
import com.checkin.app.ui.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Choosing the reminder hour is two writes — the stored hour and the armed alarm — and a chooser
 * that made only the first would deliver the next reminder at the hour the user just moved away
 * from. Both choosers go through [chooseFirstReminderHour], so it is pinned here alongside the one
 * of them a JVM suite can reach.
 */
class SettingsViewModelTest {

    private val now = 1_000L
    private val time = FakeTimeSource(now, LocalDate.of(2026, 6, 15))

    @Test
    fun `opens on the stored hour`() {
        val vm = SettingsViewModel(FakeReminderSettings(7), FakeNudgeAlarms(), time)

        assertEquals(7, vm.uiState.value.reminderHour)
    }

    @Test
    fun `a new hour is stored, re-armed and shown`() {
        val settings = FakeReminderSettings()
        val alarms = FakeNudgeAlarms()
        val vm = SettingsViewModel(settings, alarms, time)

        vm.setReminderHour(15)

        assertEquals(15, settings.firstReminderHour())
        assertEquals(listOf(now), alarms.armedFrom)
        assertEquals(15, vm.uiState.value.reminderHour)
    }

    /** The screen shows what was stored, not what was asked for, so the two cannot disagree. */
    @Test
    fun `an out-of-range hour shows as the clamped one`() {
        val vm = SettingsViewModel(FakeReminderSettings(), FakeNudgeAlarms(), time)

        vm.setReminderHour(23)

        assertEquals(NudgeSchedule.firstHourRange.last, vm.uiState.value.reminderHour)
    }

    /** A refused arm must not lose the choice: the next process start re-arms against it. */
    @Test
    fun `a throwing arm still stores the hour`() {
        val settings = FakeReminderSettings()

        val stored = settings.chooseFirstReminderHour(12, FakeNudgeAlarms(throwOnArm = true), now)

        assertEquals(12, stored)
        assertEquals(12, settings.firstReminderHour())
    }
}
