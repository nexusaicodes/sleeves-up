package com.checkin.app.session

import com.checkin.app.session.SessionRules.OpenConflict
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The record's invariants as both apps apply them: one open session across all habits, a duration
 * that is exactly stop less start and never negative, and a forgotten session closed at its own
 * midnight however late the close runs.
 */
class SessionRulesTest {

    private val utc = TimeZone.UTC
    private val kolkata = TimeZone.of("Asia/Kolkata")
    private val santiago = TimeZone.of("America/Santiago")

    // Epoch millis computed with java.time, an independent reading of the zone rules.
    private val utcJun15At0900 = 1_781_514_000_000L
    private val utcJun16Midnight = 1_781_568_000_000L
    private val kolkataJun16Midnight = 1_781_548_200_000L
    private val santiagoSep6At0100 = 1_788_667_200_000L

    private val hour = 3_600_000L

    // --- What a check-in meets ---

    @Test
    fun `a check-in with nothing open may write`() {
        assertEquals(OpenConflict.NONE, SessionRules.conflictFor(openHabitId = null, habitId = 1))
    }

    /** One open session across all habits: another habit's blocks the check-in, its own is no error. */
    @Test
    fun `an open session is this habit's or another's`() {
        assertEquals(OpenConflict.SAME_HABIT, SessionRules.conflictFor(openHabitId = 1, habitId = 1))
        assertEquals(OpenConflict.OTHER_HABIT, SessionRules.conflictFor(openHabitId = 2, habitId = 1))
    }

    // --- Duration ---

    @Test
    fun `a duration is exactly stop less start`() {
        assertEquals(45 * 60_000L, SessionRules.durationMs(utcJun15At0900, utcJun15At0900 + 45 * 60_000L))
        assertEquals(0L, SessionRules.durationMs(utcJun15At0900, utcJun15At0900))
    }

    /** A clock moved back can put the stop first; a negative duration would poison every sum. */
    @Test
    fun `a stop before the start is a zero duration`() {
        assertEquals(0L, SessionRules.durationMs(utcJun15At0900, utcJun15At0900 - hour))
    }

    // --- The day boundary ---

    @Test
    fun `a session's day ends at its date key's midnight in its own zone`() {
        assertEquals(utcJun16Midnight, SessionRules.dayBoundaryFor("2026-06-15", utcJun15At0900, utc))
        assertEquals(kolkataJun16Midnight, SessionRules.dayBoundaryFor("2026-06-15", utcJun15At0900, kolkata))
    }

    /** A midnight skipped by DST resolves to the first instant after it, as on Android. */
    @Test
    fun `a skipped midnight ends the day at the first instant after it`() {
        assertEquals(santiagoSep6At0100, SessionRules.dayBoundaryFor("2026-09-05", santiagoSep6At0100 - hour, santiago))
    }

    @Test
    fun `an unreadable date key falls back to the day of the check-in`() {
        assertEquals(utcJun16Midnight, SessionRules.dayBoundaryFor("not-a-date", utcJun15At0900, utc))
    }

    /** A late close still stamps the session's own midnight, never the hour it ran. */
    @Test
    fun `a late close stamps the boundary and not the time it ran`() {
        val stamp = SessionRules.dayBoundaryStamp(utcJun16Midnight, utcJun15At0900, utcJun16Midnight + 9 * hour)
        assertEquals(utcJun16Midnight, stamp)
    }

    @Test
    fun `the stamp is held between the check-in and now`() {
        val early = SessionRules.dayBoundaryStamp(utcJun15At0900 - hour, utcJun15At0900, utcJun16Midnight)
        assertEquals(utcJun15At0900, early)
        val future = SessionRules.dayBoundaryStamp(utcJun16Midnight, utcJun15At0900, utcJun16Midnight - hour)
        assertEquals(utcJun16Midnight - hour, future)
    }

    /** With now before the check-in (a clock moved back), now wins: a stop in the future is never right. */
    @Test
    fun `now before the check-in caps the stamp at now`() {
        val now = utcJun15At0900 - hour
        assertEquals(now, SessionRules.dayBoundaryStamp(utcJun16Midnight, utcJun15At0900, now))
    }
}
