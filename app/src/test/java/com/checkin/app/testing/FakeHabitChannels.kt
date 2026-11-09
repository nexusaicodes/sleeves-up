package com.checkin.app.testing

import com.checkin.app.data.local.Habit
import com.checkin.app.notify.HabitChannels

class FakeHabitChannels : HabitChannels {
    val synced = mutableListOf<List<Habit>>()

    override fun sync(habits: List<Habit>) {
        synced += habits
    }
}
