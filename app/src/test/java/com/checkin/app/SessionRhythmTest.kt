package com.checkin.app

import com.checkin.app.data.SessionBand
import com.checkin.app.data.SessionRhythm
import com.checkin.app.data.StartBucket
import com.checkin.app.data.local.DailyAggregate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The two descriptive splits. Nothing here ranks anything, so what is worth pinning is the shape:
 * where the bucket boundaries fall, that every bucket is always reported, and that neither average
 * divides by zero on the empty record every install starts from.
 */
class SessionRhythmTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDate.of(2026, 6, 15).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun day(key: String, sessions: Int) =
        key to DailyAggregate(key, 3_600_000L, sessions, 0L, 0L, 0, sessions, 0, 0)

    // --- Start buckets ---

    /** The boundaries themselves: noon is afternoon, 17:00 is evening, and both are inclusive. */
    @Test
    fun `the bucket boundaries fall on the named hour`() {
        assertEquals(StartBucket.MORNING, SessionRhythm.bucketOf(at(11, 59), zone))
        assertEquals(StartBucket.AFTERNOON, SessionRhythm.bucketOf(at(12, 0), zone))
        assertEquals(StartBucket.AFTERNOON, SessionRhythm.bucketOf(at(16, 59), zone))
        assertEquals(StartBucket.EVENING, SessionRhythm.bucketOf(at(17, 0), zone))
    }

    /** The small hours are morning: there is no fourth bucket and no session is filed as an outlier. */
    @Test
    fun `midnight and the last minute of the day both land in a bucket`() {
        assertEquals(StartBucket.MORNING, SessionRhythm.bucketOf(at(0, 0), zone))
        assertEquals(StartBucket.EVENING, SessionRhythm.bucketOf(at(23, 59), zone))
    }

    @Test
    fun `starts are counted into their buckets`() {
        val counts = SessionRhythm.startBuckets(
            listOf(at(9), at(10, 30), at(13), at(20), at(21)),
            zone,
        )

        assertEquals(2, counts[StartBucket.MORNING])
        assertEquals(1, counts[StartBucket.AFTERNOON])
        assertEquals(2, counts[StartBucket.EVENING])
    }

    /** A legend that gains and loses rows as the data moves is harder to read than three zeros. */
    @Test
    fun `every bucket is reported even with nothing in it`() {
        val counts = SessionRhythm.startBuckets(emptyList(), zone)

        assertEquals(StartBucket.entries.toSet(), counts.keys)
        assertEquals(0, counts[StartBucket.MORNING])
        assertEquals(0, counts[StartBucket.EVENING])
    }

    /** The zone is the caller's, so the same instant reads as a different time of day elsewhere. */
    @Test
    fun `the bucket is read in the zone it is given`() {
        val nineUtc = at(9)

        assertEquals(StartBucket.MORNING, SessionRhythm.bucketOf(nineUtc, zone))
        assertEquals(StartBucket.EVENING, SessionRhythm.bucketOf(nineUtc, ZoneId.of("Asia/Tokyo")))
    }

    // --- Sessions per day ---

    /** Counts days, not sessions: a day of five blocks is one entry, not five. */
    @Test
    fun `days are banded by how many sessions they held`() {
        val bands = SessionRhythm.sessionsPerDayBands(
            mapOf(
                day("2026-06-01", 1),
                day("2026-06-02", 2),
                day("2026-06-03", 3),
                day("2026-06-04", 5),
            ),
        )

        assertEquals(1, bands[SessionBand.ONE])
        assertEquals(1, bands[SessionBand.TWO])
        assertEquals(2, bands[SessionBand.THREE_PLUS])
    }

    @Test
    fun `every band is reported even with nothing in it`() {
        val bands = SessionRhythm.sessionsPerDayBands(emptyMap())

        assertEquals(SessionBand.entries.toSet(), bands.keys)
        assertEquals(0, bands[SessionBand.THREE_PLUS])
    }

    /**
     * Divides by the days that *had* sessions, not by the tracked window — this describes the shape
     * of a working day, and a month with time off would otherwise report a thinner rhythm than the
     * user actually worked.
     */
    @Test
    fun `the average divides by the days that had sessions`() {
        val summaries = mapOf(day("2026-06-01", 3), day("2026-06-02", 1), day("2026-06-03", 2))

        assertEquals(2f, SessionRhythm.averageSessionsPerDay(summaries), 0.001f)
    }

    /** The state every install starts in, and the one that would divide by zero. */
    @Test
    fun `an empty record averages zero rather than dividing by zero`() {
        assertEquals(0f, SessionRhythm.averageSessionsPerDay(emptyMap()), 0.001f)
    }
}
