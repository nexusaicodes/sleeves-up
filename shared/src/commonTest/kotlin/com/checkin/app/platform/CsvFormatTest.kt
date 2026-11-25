package com.checkin.app.platform

import com.checkin.app.data.local.DailyAggregate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The CSV's column order, pinned.
 *
 * People write scripts against this file, so the contract is that columns are **appended** — never
 * renamed, retyped or moved. `AndroidCsvExporter` needs a `Context` and so cannot be tested here,
 * which is exactly how a reorder could otherwise land with nothing failing.
 */
class CsvFormatTest {

    private val utc = TimeZone.UTC
    private val kolkata = TimeZone.of("Asia/Kolkata")

    @Test
    fun `the header is the agreed column order`() {
        assertEquals(
            "Date,First Check In,Last Check Out,Total Hours,Session Count,Day Boundary Check Outs," +
                "In App Check Outs,Timer Notification Check Outs,Reminder Notification Check Outs," +
                "Widget Check Outs\n",
            csvHeader(),
        )
    }

    @Test
    fun `each ending gets its own appended count and the five sum to the session count`() {
        val summary = DailyAggregate(
            dateKey = "2026-06-15",
            totalDurationMs = 2 * 3_600_000L,
            sessionCount = 4,
            firstCheckIn = 0L,
            lastCheckOut = 0L,
            dayBoundaryCheckOuts = 1,
            inAppCheckOuts = 1,
            timerNotificationCheckOuts = 1,
            reminderNotificationCheckOuts = 0,
            widgetCheckOuts = 1,
        )

        val fields = csvRow("2026-06-15", summary, utc).trimEnd('\n').split(",")

        assertEquals(10, fields.size)
        assertEquals("2026-06-15", fields[0])
        assertEquals("2.00", fields[3])
        assertEquals("4", fields[4])
        // A day worked in four blocks — one closed from the screen, one from the timer
        // notification, one from the widget, one by midnight. The columns count sessions, not
        // days, and say nothing about the day itself.
        assertEquals("1", fields[5])
        assertEquals("1", fields[6])
        assertEquals("1", fields[7])
        assertEquals("0", fields[8])
        assertEquals("1", fields[9])
        // The five endings account for every completed session, since only completed sessions
        // aggregate and each carries exactly one.
        assertEquals(fields[4].toInt(), (5..9).sumOf { fields[it].toInt() })
    }

    /** A gap-filled day states zeros rather than blanks, every ending column included. */
    @Test
    fun `a day with no sessions writes zeros in every count`() {
        val fields = csvRow("2026-06-16", null, utc).trimEnd('\n').split(",")

        assertEquals(10, fields.size)
        assertEquals("0.00", fields[3])
        (4..9).forEach { assertEquals("0", fields[it]) }
    }

    /** The habit names the file, reduced so no name can put a separator or a rejected character in it. */
    @Test
    fun `the file is named for its habit safely`() {
        assertEquals(
            "checkin_morning_run_2026-06-01_2026-06-30.csv",
            exportFileName("Morning run", "2026-06-01", "2026-06-30"),
        )
        assertEquals("checkin_a_b_2026-06-01_2026-06-30.csv", exportFileName("../a/b", "2026-06-01", "2026-06-30"))
        assertEquals("checkin_café_2026-06-01_2026-06-30.csv", exportFileName("Café!", "2026-06-01", "2026-06-30"))
    }

    @Test
    fun `a habit with no usable name still gets a file`() {
        assertEquals("checkin_habit_2026-06-01_2026-06-30.csv", exportFileName(null, "2026-06-01", "2026-06-30"))
        assertEquals("checkin_habit_2026-06-01_2026-06-30.csv", exportFileName("!!!", "2026-06-01", "2026-06-30"))
    }

    private fun clocks(firstCheckIn: Long, lastCheckOut: Long, zone: TimeZone): List<String> {
        val summary = DailyAggregate("2026-06-15", 3_600_000L, 1, firstCheckIn, lastCheckOut, 0, 1, 0, 0, 0)
        return csvRow("2026-06-15", summary, zone).split(",").subList(1, 3)
    }

    /**
     * The file is machine-read, so the clock columns are "hh:mm AM" in ASCII digits and English
     * whatever the reader's language, and nothing here reads a locale. Expected values are what
     * `hh:mm a` in `Locale.US` writes on the JVM, for the same instants and zones.
     */
    @Test
    fun `the clock columns are twelve-hour ASCII in the zone given`() {
        assertEquals(listOf("05:06 AM", "12:00 PM"), clocks(1_781_500_000_000L, 1_781_524_800_000L, utc))
        assertEquals(listOf("10:36 AM", "05:30 PM"), clocks(1_781_500_000_000L, 1_781_524_800_000L, kolkata))
    }

    /** Midnight is 12 AM, the minute before is 11:59 PM, and neither hour is ever written as 00. */
    @Test
    fun `midnight and the last minute of the day read as 12 AM and 11 59 PM`() {
        assertEquals(listOf("12:00 AM", "11:59 PM"), clocks(1_781_481_600_000L, 1_781_567_940_000L, utc))
    }

    private fun hours(durationMs: Long): String {
        val summary = DailyAggregate("2026-06-15", durationMs, 1, 0L, 0L, 0, 1, 0, 0, 0)
        return csvRow("2026-06-15", summary, utc).split(",")[3]
    }

    /**
     * Rounded half up to the hundredth, as Java's `%.2f` rounds: a duration exactly halfway between
     * two hundredths of an hour (every 18 seconds past a multiple of 36) goes up, never to the even
     * neighbour. Expected values are `String.format(Locale.US, "%.2f", ms / 3_600_000.0)` on the JVM.
     */
    @Test
    fun `total hours round half up as the JVM wrote them`() {
        val expected = mapOf(
            0L to "0.00",
            17_999L to "0.00",
            18_000L to "0.01",
            54_000L to "0.02",
            90_000L to "0.03",
            1_800_000L to "0.50",
            3_599_999L to "1.00",
            5_400_000L to "1.50",
            123_456_789L to "34.29",
            -18_000L to "-0.01",
            -1L to "-0.00",
        )
        expected.forEach { (ms, text) -> assertEquals(text, hours(ms), "$ms ms") }
    }

    /** Harakat are marks, not letters; they stay with the word rather than splitting it. */
    @Test
    fun `an arabic name keeps its diacritics`() {
        assertEquals(
            "checkin_قِراءة_2026-06-01_2026-06-30.csv",
            exportFileName("قِراءة", "2026-06-01", "2026-06-30"),
        )
    }

    @Test
    fun `the file is the header then one row per day with both ends included and gaps as zeros`() {
        val worked = DailyAggregate(
            dateKey = "2026-06-30",
            totalDurationMs = 3_600_000L,
            sessionCount = 1,
            firstCheckIn = 0L,
            lastCheckOut = 0L,
            dayBoundaryCheckOuts = 0,
            inAppCheckOuts = 1,
            timerNotificationCheckOuts = 0,
            reminderNotificationCheckOuts = 0,
            widgetCheckOuts = 0,
        )

        val lines = csvLines("2026-06-29", "2026-07-01", mapOf(worked.dateKey to worked), utc).toList()

        assertEquals(
            listOf(
                csvHeader(),
                csvRow("2026-06-29", null, utc),
                csvRow("2026-06-30", worked, utc),
                csvRow("2026-07-01", null, utc),
            ),
            lines,
        )
    }

    @Test
    fun `a one-day range is the header and that day`() {
        assertEquals(
            listOf(csvHeader(), csvRow("2026-06-15", null, utc)),
            csvLines("2026-06-15", "2026-06-15", emptyMap(), utc).toList(),
        )
    }
}
