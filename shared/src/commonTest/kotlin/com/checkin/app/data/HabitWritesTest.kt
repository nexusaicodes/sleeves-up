package com.checkin.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitWritesTest {

    private data class TestHabit(
        override val id: Long,
        override val name: String?,
        override val archived: Boolean = false,
    ) : HabitFacts {
        override val uuid: String = "u$id"
    }

    private val gym = TestHabit(1, "Gym")
    private val reading = TestHabit(2, "Reading")
    private val guitar = TestHabit(3, "Guitar")

    @Test
    fun `an active habit can be selected with nothing running`() {
        assertTrue(HabitWrites.canSelect(reading, runningHabitId = null))
    }

    @Test
    fun `the running habit can be selected`() {
        assertTrue(HabitWrites.canSelect(reading, runningHabitId = 2))
    }

    @Test
    fun `no other habit can be selected while one is running`() {
        assertFalse(HabitWrites.canSelect(reading, runningHabitId = 1))
    }

    @Test
    fun `an archived or missing habit cannot be selected`() {
        assertFalse(HabitWrites.canSelect(reading.copy(archived = true), runningHabitId = null))
        assertFalse(HabitWrites.canSelect(null, runningHabitId = null))
    }

    @Test
    fun `the running habit cannot be archived`() {
        assertEquals(ArchiveResult.RUNNING, HabitWrites.archiveRefusal(2, runningHabitId = 2, listOf(gym, reading)))
    }

    @Test
    fun `the running refusal comes before the last-active one`() {
        assertEquals(ArchiveResult.RUNNING, HabitWrites.archiveRefusal(1, runningHabitId = 1, listOf(gym)))
    }

    @Test
    fun `the last active habit cannot be archived`() {
        assertEquals(ArchiveResult.LAST_ACTIVE, HabitWrites.archiveRefusal(1, runningHabitId = null, listOf(gym)))
    }

    @Test
    fun `a habit can be archived while another one runs`() {
        assertNull(HabitWrites.archiveRefusal(2, runningHabitId = 1, listOf(gym, reading)))
    }

    @Test
    fun `archiving the selected habit moves the selection to the first habit left`() {
        assertEquals(1L, HabitWrites.selectionAfterArchive(2, selectedId = 2, listOf(gym, reading, guitar)))
        assertEquals(2L, HabitWrites.selectionAfterArchive(1, selectedId = 1, listOf(gym, reading, guitar)))
    }

    @Test
    fun `archiving another habit leaves the selection alone`() {
        assertNull(HabitWrites.selectionAfterArchive(3, selectedId = 1, listOf(gym, reading, guitar)))
        assertNull(HabitWrites.selectionAfterArchive(3, selectedId = null, listOf(gym, reading, guitar)))
    }

    @Test
    fun `a restored habit whose name an active habit took since is refused`() {
        val archived = TestHabit(4, " gym ", archived = true)
        assertEquals(NameProblem.DUPLICATE, HabitWrites.restoreProblem(archived, listOf(gym, reading)))
    }

    @Test
    fun `a restored habit with a free name or none is allowed`() {
        assertNull(HabitWrites.restoreProblem(TestHabit(4, "Swim", archived = true), listOf(gym, reading)))
        assertNull(HabitWrites.restoreProblem(TestHabit(4, null, archived = true), listOf(gym, reading)))
    }
}
