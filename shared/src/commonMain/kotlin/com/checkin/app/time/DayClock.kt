package com.checkin.app.time

import com.checkin.app.util.MILLIS_PER_HOUR
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Pure timing for the day-rollover flow so it can be unit-tested without a real clock. */
object DayClock {
    /** Cap so a timezone/DST change is picked up within the hour without a broadcast receiver. */
    const val MAX_TICK_MS = MILLIS_PER_HOUR

    /** Millis from [nowMs] to the next midnight in [zone], capped at [MAX_TICK_MS]. */
    fun millisUntilNextMidnight(nowMs: Long, zone: TimeZone): Long {
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date
        val nextMidnightMs = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        return (nextMidnightMs - nowMs).coerceIn(0L, MAX_TICK_MS)
    }
}
