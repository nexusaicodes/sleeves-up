package com.checkin.app

import com.checkin.app.data.local.Habit
import com.checkin.app.ui.presence.TapOutcome
import com.checkin.app.ui.presence.TapRouting
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every tap from outside the app is routed against the rows as they are at the tap, so a surface
 * drawn hours ago — a nudge in the tray, a widget on the home screen — can never open a gate the
 * repository would then refuse, or write to a habit the user did not mean.
 */
class TapRoutingTest {

    private fun habit(id: Long, archived: Boolean = false) =
        Habit(id = id, uuid = "u$id", name = "Habit $id", archived = archived, createdAt = 0L, channelId = "c$id")

    private val reading = habit(1)
    private val running = habit(2)

    @Test
    fun `a tap for a habit with nothing open checks in to it`() {
        assertEquals(TapOutcome.CheckIn(1), TapRouting.forHabit(reading, runningHabitId = null))
    }

    @Test
    fun `a tap for an unknown or archived habit asks for nothing`() {
        // An orphaned uuid after a restore, and a nudge for a habit archived since it was posted.
        assertEquals(TapOutcome.NoAction, TapRouting.forHabit(null, runningHabitId = null))
        assertEquals(TapOutcome.NoAction, TapRouting.forHabit(habit(3, archived = true), runningHabitId = null))
    }

    @Test
    fun `a tap for the habit already running asks for nothing`() {
        assertEquals(TapOutcome.NoAction, TapRouting.forHabit(running, runningHabitId = running.id))
    }

    /** One open session across all habits: the gate would end in a check-in the repository refuses. */
    @Test
    fun `a tap for one habit while another runs says so and opens nothing`() {
        assertEquals(TapOutcome.RunningElsewhere(running.id), TapRouting.forHabit(reading, runningHabitId = running.id))
    }

    @Test
    fun `the widget offers the open session's check-out whatever the habits`() {
        assertEquals(TapOutcome.CheckOut, TapRouting.forWidget(listOf(reading, running), runningHabitId = running.id))
        assertEquals(TapOutcome.CheckOut, TapRouting.forWidget(listOf(running), runningHabitId = running.id))
    }

    @Test
    fun `the widget goes straight to the gate when there is only one habit`() {
        assertEquals(TapOutcome.CheckIn(1), TapRouting.forWidget(listOf(reading), runningHabitId = null))
    }

    @Test
    fun `the widget asks which habit when there is a choice`() {
        assertEquals(TapOutcome.ChooseHabit, TapRouting.forWidget(listOf(reading, running), runningHabitId = null))
    }

    /** Unreachable through the archive rules, which keep one habit active; nothing is the safe answer. */
    @Test
    fun `the widget asks for nothing with no active habit`() {
        assertEquals(TapOutcome.NoAction, TapRouting.forWidget(emptyList(), runningHabitId = null))
    }
}
