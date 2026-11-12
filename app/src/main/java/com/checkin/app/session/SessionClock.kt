package com.checkin.app.session

/**
 * The two numbers a session's elapsed time is drawn from, kept out of [CheckInService] so they are
 * JVM-unit-testable — a `Service` is not, and this is the most visible figure the app has, the one a
 * user reads to decide whether to check out.
 *
 * The notification uses [chronometerBase] and lets the platform count; the Check-In screen's ticker
 * uses [elapsedMs] once a second. **Both go through here**, so a hand-rolled copy of the subtraction
 * cannot fix the floor rule in one and miss it in the other.
 *
 * Elapsed time is plain wall-clock since check-in; nothing subtracts from it.
 */
object SessionClock {

    /**
     * The instant a platform chronometer should count up from, or null when there is nothing for one
     * to count.
     *
     * Null for a non-positive start: a service posting before it has adopted a row would otherwise
     * count up from the epoch and flash a decades-long timer for the instant before the reconcile
     * corrects it.
     */
    fun chronometerBase(startTime: Long): Long? = startTime.takeIf { it > 0L }

    /**
     * Elapsed time at [nowMs]. Never negative: a clock that has run backwards is a corrupt row or a
     * changed system clock, and showing nothing is better than showing a negative duration.
     */
    fun elapsedMs(nowMs: Long, startTime: Long): Long {
        if (startTime <= 0L) return 0L
        return (nowMs - startTime).coerceAtLeast(0L)
    }
}
