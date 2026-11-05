package com.checkin.app

import com.checkin.app.notify.nudge.NudgeVariant
import com.checkin.app.notify.nudge.NudgeVoice
import com.checkin.app.notify.nudge.NudgeVoice.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The voice decides only which pool the copy comes from, and the pick only which line in it. Both are
 * pure, so the boundaries are pinned here: when a habit reads as returning, and that a pool turns all
 * the way round before any line repeats.
 */
class NudgeVoiceTest {

    private val today = LocalDate.of(2026, 11, 2)

    private data class V(override val key: String) : NudgeVariant

    private val pool = (1..12).map { V("p.b$it") }

    @Test
    fun `no session ever is not started`() {
        assertEquals(Voice.NOT_STARTED, NudgeVoice.voiceFor(null, today))
    }

    @Test
    fun `a session within two days is steady and from three is returning`() {
        assertEquals(Voice.STEADY, NudgeVoice.voiceFor(today.minusDays(1), today))
        assertEquals(Voice.STEADY, NudgeVoice.voiceFor(today.minusDays(2), today))
        assertEquals(Voice.RETURNING, NudgeVoice.voiceFor(today.minusDays(3), today))
        assertEquals(Voice.RETURNING, NudgeVoice.voiceFor(today.minusDays(30), today))
    }

    /** A clock moved back can leave the last session "after" today; that is no lapse. */
    @Test
    fun `a last session after today reads as steady`() {
        assertEquals(Voice.STEADY, NudgeVoice.voiceFor(today.plusDays(1), today))
    }

    @Test
    fun `a never-shown variant is picked before any shown one`() {
        val shown = pool.dropLast(1).associate { it.key to 100L }

        assertEquals(pool.last(), NudgeVoice.pick(pool, shown, seed = 7))
    }

    @Test
    fun `with every variant shown, the one shown longest ago is picked`() {
        val shown = pool.mapIndexed { i, v -> v.key to (1_000L - i) }.toMap()

        assertEquals(pool.last(), NudgeVoice.pick(pool, shown, seed = 7))
    }

    /** History for keys no longer in the pool, or another pool's, changes nothing. */
    @Test
    fun `history outside the pool is ignored`() {
        val shown = mapOf("gone.b1" to 5L, "other.b2" to 9L)

        assertEquals(NudgeVoice.pick(pool, emptyMap(), seed = 3), NudgeVoice.pick(pool, shown, seed = 3))
    }

    @Test
    fun `ties fall to the seed, deterministically`() {
        assertEquals(NudgeVoice.pick(pool, emptyMap(), seed = 42), NudgeVoice.pick(pool, emptyMap(), seed = 42))
        val picks = (0L until pool.size).map { NudgeVoice.pick(pool, emptyMap(), seed = it) }.toSet()
        assertEquals(pool.toSet(), picks)
    }

    /** Whatever the seeds, a pool's length of picks shows every line once. */
    @Test
    fun `a full turn of picks shows every variant once`() {
        val shown = mutableMapOf<String, Long>()
        val picked = (0 until pool.size).map { day ->
            NudgeVoice.pick(pool, shown, seed = day * 7919L).also { shown[it.key] = day.toLong() }
        }

        assertEquals(pool.toSet(), picked.toSet())
        assertEquals(picked.first(), NudgeVoice.pick(pool, shown, seed = 1))
    }

    @Test
    fun `the seed is stable within a day and moves across days`() {
        val uuid = "a3f1c2"
        assertEquals(NudgeVoice.seedFor(uuid, today), NudgeVoice.seedFor(uuid, today))
        assertNotEquals(NudgeVoice.seedFor(uuid, today), NudgeVoice.seedFor(uuid, today.plusDays(1)))
        assertNotEquals(NudgeVoice.seedFor(uuid, today), NudgeVoice.seedFor("b7e9d4", today))
    }
}
