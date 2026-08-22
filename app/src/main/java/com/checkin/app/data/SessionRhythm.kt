package com.checkin.app.data

import com.checkin.app.data.local.DailyAggregate
import java.time.Instant
import java.time.ZoneId

/** When in the day a session began. Descriptive: no bucket is better than another. */
enum class StartBucket {
    MORNING,
    AFTERNOON,
    EVENING,
}

/** How many sessions a day held. Descriptive: more is not better, it is a different rhythm. */
enum class SessionBand {
    ONE,
    TWO,
    THREE_PLUS,
}

/**
 * Pure descriptions of *how* the user works, as opposed to how much.
 *
 * Nothing here ranks anything, and nothing here may grow a baseline. These figures exist so a user
 * can recognise their own pattern — that they start late, that they work in two blocks — and every
 * one of them is a plain count with no preferable direction. A "best time to start" or a "most
 * productive block" would be the deleted daily target wearing a different hat.
 *
 * Android-free and clock-free: the caller supplies the zone, so the buckets are testable on the JVM
 * and a device in another zone is not a different code path.
 */
object SessionRhythm {

    /** Afternoon begins at noon; evening at 17:00. Boundaries are inclusive of the named hour. */
    const val AFTERNOON_FROM_HOUR = 12
    const val EVENING_FROM_HOUR = 17

    /** Days holding this many sessions or more all land in [SessionBand.THREE_PLUS]. */
    const val THREE_PLUS_FROM = 3

    fun bucketOf(startedAtMillis: Long, zone: ZoneId): StartBucket {
        val hour = Instant.ofEpochMilli(startedAtMillis).atZone(zone).hour
        return when {
            hour >= EVENING_FROM_HOUR -> StartBucket.EVENING
            hour >= AFTERNOON_FROM_HOUR -> StartBucket.AFTERNOON
            else -> StartBucket.MORNING
        }
    }

    /**
     * [startsMillis] bucketed by local start hour.
     *
     * Every bucket is present in the result, at zero if nothing landed in it — a legend that gains
     * and loses rows as the data moves is harder to read than one that states three figures.
     */
    fun startBuckets(startsMillis: List<Long>, zone: ZoneId): Map<StartBucket, Int> {
        val counts = StartBucket.entries.associateWith { 0 }.toMutableMap()
        startsMillis.forEach { millis ->
            val bucket = bucketOf(millis, zone)
            counts[bucket] = (counts[bucket] ?: 0) + 1
        }
        return counts
    }

    /**
     * Days grouped by how many completed sessions they held, every band present.
     *
     * Counts *days*, not sessions: the question is how often a day is broken into blocks, so a day
     * with five sessions is one entry in [SessionBand.THREE_PLUS] rather than five of anything.
     */
    fun sessionsPerDayBands(summaries: Map<String, DailyAggregate>): Map<SessionBand, Int> {
        val counts = SessionBand.entries.associateWith { 0 }.toMutableMap()
        summaries.values.forEach { day ->
            val band = when {
                day.sessionCount >= THREE_PLUS_FROM -> SessionBand.THREE_PLUS
                day.sessionCount == 2 -> SessionBand.TWO
                day.sessionCount == 1 -> SessionBand.ONE
                // A day in the map with no completed session cannot happen — the aggregate query
                // filters them — but a zero must not be counted as a one if that ever changes.
                else -> return@forEach
            }
            counts[band] = (counts[band] ?: 0) + 1
        }
        return counts
    }

    /**
     * Mean completed sessions per day the user showed up.
     *
     * Divides by the days that *have* sessions rather than by the tracked window, because this
     * describes the shape of a working day rather than how many of them there were — days shown up
     * is the figure that answers the second question, and mixing them would make a consistent
     * two-block worker's rhythm look like it thinned out over a month they took time off in.
     *
     * Zero when nothing is recorded, rather than dividing by zero.
     */
    fun averageSessionsPerDay(summaries: Map<String, DailyAggregate>): Float =
        if (summaries.isEmpty()) 0f else ConsistencyStats.totalSessions(summaries).toFloat() / summaries.size
}
