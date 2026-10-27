package com.checkin.app

import com.checkin.app.data.local.Habit
import com.checkin.app.widget.WidgetFace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widget shows names and whether a session is open, and nothing that measures a habit — so these
 * pin which face each state gets, and that only a change the widget would show reaches it.
 */
class WidgetFaceTest {

    private fun habit(id: Long, name: String? = "Habit $id") =
        Habit(id = id, uuid = "u$id", name = name, createdAt = 0L, channelId = "c$id")

    private val reading = habit(1, "Reading")
    private val running = habit(2, "Running")
    private val guitar = habit(3, "Guitar")

    @Test
    fun `several habits cycle through every name in the order given`() {
        assertEquals(
            WidgetFace.Cycling(listOf("Reading", "Running", "Guitar")),
            WidgetFace.of(listOf(reading, running, guitar), runningHabitId = null, animationsOn = true),
        )
    }

    /** A cycle is motion; with animations off the widget holds still. */
    @Test
    fun `several habits with animations off read a still check-in`() {
        assertEquals(
            WidgetFace.Plain,
            WidgetFace.of(listOf(reading, running), runningHabitId = null, animationsOn = false),
        )
    }

    @Test
    fun `one habit is named outright, animations or not`() {
        assertEquals(
            WidgetFace.Single("Reading"),
            WidgetFace.of(listOf(reading), runningHabitId = null, animationsOn = true),
        )
        assertEquals(
            WidgetFace.Single("Reading"),
            WidgetFace.of(listOf(reading), runningHabitId = null, animationsOn = false),
        )
    }

    @Test
    fun `an open session is the whole face, whatever else there is`() {
        assertEquals(
            WidgetFace.Running("Running"),
            WidgetFace.of(listOf(reading, running, guitar), runningHabitId = running.id, animationsOn = true),
        )
    }

    /** Owed a name: drawn as plain "Check In" rather than an empty "Check in to". */
    @Test
    fun `an unnamed habit carries no name`() {
        val unnamed = habit(4, name = null)
        assertEquals(
            WidgetFace.Single(null),
            WidgetFace.of(listOf(unnamed), runningHabitId = null, animationsOn = true),
        )
        assertEquals(
            WidgetFace.Cycling(listOf("Reading", null)),
            WidgetFace.of(listOf(reading, unnamed), runningHabitId = null, animationsOn = true),
        )
    }

    /** Unreachable through the archive rules; a still face is the safe answer. */
    @Test
    fun `no active habit reads a still check-in`() {
        assertEquals(WidgetFace.Plain, WidgetFace.of(emptyList(), runningHabitId = null, animationsOn = true))
    }

    /**
     * Room re-emits on every write to either table, and each redraw restarts the widget's cycle — so a
     * write the widget would not show (a row it does not draw, a copy of an unchanged habit) must not reach it.
     */
    @Test
    fun `only a change the widget would show is let through`() = runTest {
        val habits = MutableStateFlow(listOf(reading, running))
        val runningId = MutableStateFlow<Long?>(null)
        val seen = mutableListOf<WidgetFace>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            WidgetFace.faces(habits, runningId) { true }.toList(seen)
        }

        habits.value = listOf(reading.copy(), running.copy()) // a write that changed nothing shown
        runningId.value = running.id
        runningId.value = running.id
        habits.value = listOf(reading, running.copy(name = "Run"))
        job.cancel()

        assertEquals(
            listOf(
                WidgetFace.Cycling(listOf("Reading", "Running")),
                WidgetFace.Running("Running"),
                WidgetFace.Running("Run"),
            ),
            seen,
        )
    }

    @Test
    fun `the animation setting is read with each change`() = runTest {
        var animations = true
        val faces = WidgetFace.faces(flowOf(listOf(reading, running)), flowOf(null)) { animations }
        assertEquals(listOf(WidgetFace.Cycling(listOf("Reading", "Running"))), faces.take(1).toList())

        animations = false
        assertEquals(listOf(WidgetFace.Plain), faces.take(1).toList())
    }
}
