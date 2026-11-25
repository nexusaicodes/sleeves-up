package com.checkin.app.data.local

import com.checkin.app.notify.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitTest {

    /**
     * The seed habit's channel is the one a pre-habits install already had, so the database must seed
     * exactly that id — `data/` restates it rather than importing it from `notify/`.
     */
    @Test
    fun `the seed habit keeps the pre-habits reminder channel`() {
        assertEquals(NotificationChannels.NUDGE, Habit.SEED_CHANNEL_ID)
    }
}
