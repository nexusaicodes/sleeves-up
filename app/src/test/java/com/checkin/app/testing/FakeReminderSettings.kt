package com.checkin.app.testing

import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.SharedPrefsReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeReminderSettings(vararg minutes: Pair<String, Int>) : ReminderSettings {
    private val stored = MutableStateFlow(minutes.associate { (uuid, m) -> uuid to NudgeSchedule.clampMinute(m) })

    override fun reminderMinute(habitUuid: String): Int = stored.value[habitUuid] ?: NudgeSchedule.DEFAULT_MINUTE

    override fun setReminderMinute(habitUuid: String, minute: Int) {
        stored.value = stored.value + (habitUuid to NudgeSchedule.clampMinute(minute))
    }

    var active: Set<String>? = null
        private set

    override fun earliestMinute(): Int = SharedPrefsReminderSettings.earliest(stored.value, active)

    override fun rememberActive(habitUuids: Set<String>) {
        active = habitUuids
    }

    override fun changes(): Flow<Unit> = stored.map { }
}
