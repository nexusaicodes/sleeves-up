package com.checkin.app.notify.nudge

import com.checkin.app.notify.nudge.NudgeVoice.Voice
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NudgePlanTest {

    private val hour = 3_600_000L
    private val minute = 60_000L
    private val day = 24 * hour

    // 2026-06-15 00:00 UTC; UTC wall times need no zone rules.
    private val jun15 = 1_781_481_600_000L

    private data class Key(override val key: String) : NudgeVariant

    private val copy: (Voice) -> VoiceKeys = { voice ->
        val prefix = voice.name.lowercase()
        VoiceKeys(listOf(Key("$prefix.t1"), Key("$prefix.t2")), listOf(Key("$prefix.b1"), Key("$prefix.b2")))
    }

    private fun habit(
        id: Long,
        minuteOfDay: Int = 10 * 60,
        lastSession: LocalDate? = null,
        sessionToday: Boolean = false,
        shownToday: Boolean = false,
    ) = PlanHabit(id, "uuid-$id", minuteOfDay, lastSession, sessionToday, shownToday, emptyMap())

    private fun plan(vararg habits: PlanHabit, now: Long = jun15 + 9 * hour, lastShown: Long? = null) =
        NudgePlan.plan(habits.toList(), now, TimeZone.UTC, lastShown, copy)

    @Test
    fun `a habit is reminded once a day at its minute for as many days as fit`() {
        val planned = plan(habit(1))
        assertEquals(NudgePlan.MAX_DAYS, planned.size)
        assertEquals((0 until NudgePlan.MAX_DAYS).map { jun15 + it * day + 10 * hour }, planned.map { it.fireAtMs })
        assertTrue(planned.all { it.voice == Voice.NOT_STARTED })
    }

    @Test
    fun `the plan shrinks to keep every habit inside the pending limit`() {
        assertEquals(10, NudgePlan.daysAhead(habitCount = 5))
        assertEquals(0, NudgePlan.daysAhead(habitCount = 0))
        val planned = plan(*(1L..5L).map { habit(it, minuteOfDay = 5 * 60 + it.toInt() * 120) }.toTypedArray())
        assertEquals(50, planned.size)
        assertTrue(planned.size <= NudgePlan.PENDING_LIMIT - NudgePlan.RESERVED)
    }

    @Test
    fun `a day already holding a session or a shown reminder plans nothing for today`() {
        assertEquals(jun15 + day + 10 * hour, plan(habit(1, sessionToday = true)).first().fireAtMs)
        assertEquals(jun15 + day + 10 * hour, plan(habit(1, shownToday = true)).first().fireAtMs)
    }

    @Test
    fun `a band already open is put off by the due-now delay and dropped past midnight`() {
        val afternoon = jun15 + 15 * hour
        assertEquals(afternoon + NudgeConfig.DUE_NOW_DELAY_MS, plan(habit(1), now = afternoon).first().fireAtMs)

        val lateNight = jun15 + 23 * hour + 45 * minute
        assertEquals(jun15 + day + 10 * hour, plan(habit(1), now = lateNight).first().fireAtMs)
    }

    @Test
    fun `the gap defers a later habit's reminder and holds after the last one shown`() {
        val planned = plan(habit(1), habit(2, minuteOfDay = 10 * 60 + 30))
        assertEquals(listOf(jun15 + 10 * hour, jun15 + 11 * hour + 30 * minute), planned.take(2).map { it.fireAtMs })

        val afterShown = plan(habit(1), lastShown = jun15 + 9 * hour + 45 * minute)
        assertEquals(jun15 + 11 * hour + 15 * minute, afterShown.first().fireAtMs)
    }

    @Test
    fun `a reminder the gap pushes past midnight is dropped for that day`() {
        val now = jun15 + 22 * hour + 50 * minute
        val planned = plan(habit(1, minuteOfDay = 21 * 60), now = now, lastShown = jun15 + 22 * hour + 45 * minute)
        assertEquals(jun15 + day + 21 * hour, planned.first().fireAtMs)
    }

    @Test
    fun `later days turn returning as empty days add up after a session`() {
        val voices = plan(habit(1, sessionToday = true)).map { it.voice }
        assertEquals(listOf(Voice.STEADY, Voice.STEADY, Voice.RETURNING), voices.take(3), "days 16, 17 and 18")

        val lapsed = plan(habit(1, lastSession = LocalDate(2026, 6, 10)))
        assertTrue(lapsed.all { it.voice == Voice.RETURNING })
    }

    @Test
    fun `wording turns all the way round before a line repeats`() {
        val planned = plan(habit(1))
        assertNotEquals(planned[0].titleKey, planned[1].titleKey)
        assertEquals(planned[0].titleKey, planned[2].titleKey)
        assertNotEquals(planned[0].bodyKey, planned[1].bodyKey)
        assertEquals(planned[0].bodyKey, planned[2].bodyKey)
    }

    @Test
    fun `wording shown before the plan is the first to wait`() {
        val shown = PlanHabit(1, "uuid-1", 10 * 60, null, false, false, mapOf("not_started.t1" to jun15 - day))
        val planned = NudgePlan.plan(listOf(shown), jun15 + 9 * hour, TimeZone.UTC, null, copy)
        assertEquals("not_started.t2", planned.first().titleKey)
    }
}
