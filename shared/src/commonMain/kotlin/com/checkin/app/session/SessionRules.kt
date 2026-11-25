package com.checkin.app.session

import kotlinx.datetime.TimeZone

/**
 * The rules every writer of the session record follows, whichever app and whichever storage holds
 * it: what a check-in runs into, how long a closed session lasted, and the instant a forgotten one is
 * closed at. Pure, so `SessionRulesTest` pins them on both platforms; the Android app applies them in
 * `CheckInRepository` and `SessionLifecycle`, under the locks that make each read-and-write atomic.
 */
object SessionRules {

    /** What a check-in for one habit meets in the record. */
    enum class OpenConflict {
        /** Nothing is open: the check-in may write its row. */
        NONE,

        /** This habit's session is already open: nothing to write, and nothing went wrong. */
        SAME_HABIT,

        /** Another habit's session is open, and there is one open session across all habits. */
        OTHER_HABIT,
    }

    /** What a check-in for [habitId] meets, given the habit of the open session, or null with none open. */
    fun conflictFor(openHabitId: Long?, habitId: Long): OpenConflict = when (openHabitId) {
        null -> OpenConflict.NONE
        habitId -> OpenConflict.SAME_HABIT
        else -> OpenConflict.OTHER_HABIT
    }

    /**
     * A closed session's duration: exactly its stop less its start, floored at zero. Nothing else is
     * ever subtracted. The floor is for a changed clock or a corrupt row, since one negative duration
     * would poison every sum built on the record.
     */
    fun durationMs(startedAtMs: Long, stoppedAtMs: Long): Long = (stoppedAtMs - startedAtMs).coerceAtLeast(0L)

    /**
     * The midnight that ends the session checked in at [startedAtMs] on [dateKey], read in [zone],
     * the zone it was checked in under. It goes through the `date_key`, falling back to the day
     * of [startedAtMs] only for a key that does not parse.
     */
    fun dayBoundaryFor(dateKey: String, startedAtMs: Long, zone: TimeZone): Long =
        SessionSchedule.dayBoundaryOf(dateKey, zone) ?: SessionSchedule.nextDayBoundaryAfter(startedAtMs, zone)

    /**
     * The stop instant of a session closed at its day boundary, which nobody chose: [boundaryAtMs]
     * (the instant an Android alarm was armed for, or else [dayBoundaryFor]), held no earlier than the
     * check-in and no later than [nowMs], in that order. When a clock moved back puts now before the
     * check-in, now wins: a stop in the future is never right.
     */
    fun dayBoundaryStamp(boundaryAtMs: Long, startedAtMs: Long, nowMs: Long): Long =
        boundaryAtMs.coerceAtLeast(startedAtMs).coerceAtMost(nowMs)
}
