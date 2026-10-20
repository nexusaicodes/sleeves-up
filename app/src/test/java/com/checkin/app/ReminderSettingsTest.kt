package com.checkin.app

import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.SharedPrefsReminderSettings.Companion.earliest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSettingsTest {

    /** An archived habit keeps its time for a restore, but the fallback alarm is the active habits'. */
    @Test
    fun `the fallback reads only the active habits once they are known`() {
        val stored = mapOf("archived" to 5 * 60, "gym" to 18 * 60)
        assertEquals(18 * 60, earliest(stored, active = setOf("gym")))
    }

    @Test
    fun `an active habit with no stored time is reminded at the default`() {
        assertEquals(NudgeSchedule.DEFAULT_MINUTE, earliest(mapOf("gym" to 18 * 60), active = setOf("gym", "new")))
    }

    @Test
    fun `before the active set is known every stored time and the default count`() {
        assertEquals(5 * 60, earliest(mapOf("archived" to 5 * 60), active = null))
        assertEquals(NudgeSchedule.DEFAULT_MINUTE, earliest(mapOf("gym" to 18 * 60), active = null))
    }

    @Test
    fun `no active habits falls back to the default`() {
        assertEquals(NudgeSchedule.DEFAULT_MINUTE, earliest(mapOf("archived" to 5 * 60), active = emptySet()))
    }
}
