package com.checkin.app.data

import com.checkin.app.util.MILLIS_PER_HOUR
import java.time.Duration
import java.time.ZonedDateTime

/** Pure timing for the day-rollover flow so it can be unit-tested without a real clock. */
object DayClock {
    /** Cap so a timezone/DST change is picked up within the hour without a broadcast receiver. */
    const val MAX_TICK_MS = MILLIS_PER_HOUR

    fun millisUntilNextMidnight(now: ZonedDateTime): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        return Duration.between(now, nextMidnight).toMillis().coerceIn(0L, MAX_TICK_MS)
    }
}
