package com.checkin.app

import com.checkin.app.data.HabitRules
import com.checkin.app.data.NameProblem
import com.checkin.app.data.local.Habit
import com.checkin.app.notify.NotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitRulesTest {

    private fun habit(id: Long, name: String?, archived: Boolean = false) =
        Habit(id = id, uuid = "u$id", name = name, archived = archived, createdAt = 0L, channelId = "c$id")

    @Test
    fun `names are trimmed and their inner whitespace collapsed`() {
        assertEquals("Morning run", HabitRules.normalise("  Morning \t  run "))
    }

    @Test
    fun `a blank or over-long name is refused`() {
        assertEquals(NameProblem.BLANK, HabitRules.nameProblem("   ", emptyList()))
        assertEquals(
            NameProblem.TOO_LONG,
            HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH + 1), emptyList()),
        )
        assertNull(HabitRules.nameProblem("x".repeat(HabitRules.MAX_NAME_LENGTH), emptyList()))
    }

    @Test
    fun `a duplicate is judged ignoring case and against other habits only`() {
        val active = listOf(habit(1, "Gym"))

        assertEquals(NameProblem.DUPLICATE, HabitRules.nameProblem(" gym ", active))
        // Renaming a habit to its own name is not a clash with itself.
        assertNull(HabitRules.nameProblem("GYM", active, forId = 1))
    }

    @Test
    fun `the open session's habit outranks the stored selection`() {
        val active = listOf(habit(1, "Gym"), habit(2, "Writing"))

        assertEquals(2L, HabitRules.effectiveSelection(storedId = 1, runningHabitId = 2, activeHabits = active)?.id)
        assertEquals(1L, HabitRules.effectiveSelection(storedId = 1, runningHabitId = null, activeHabits = active)?.id)
        assertEquals(1L, HabitRules.effectiveSelection(storedId = 9, runningHabitId = null, activeHabits = active)?.id)
        assertNull(HabitRules.effectiveSelection(storedId = 1, runningHabitId = null, activeHabits = emptyList()))
    }

    /**
     * The seed habit's channel is the one a pre-habits install already had, so the database must seed
     * exactly that id — `data/` restates it rather than importing it from `notify/`.
     */
    @Test
    fun `the seed habit keeps the pre-habits reminder channel`() {
        assertEquals(NotificationChannels.NUDGE, Habit.SEED_CHANNEL_ID)
    }
}
