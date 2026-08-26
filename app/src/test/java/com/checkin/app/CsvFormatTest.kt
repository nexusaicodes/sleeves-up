package com.checkin.app

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.platform.csvHeader
import com.checkin.app.platform.csvRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The CSV's column order, pinned.
 *
 * People write scripts against this file, so the contract is that columns are **appended** — never
 * renamed, retyped or moved. `DefaultCsvExporter` needs a `Context` and so cannot be tested here,
 * which is exactly how a reorder could otherwise land with nothing failing.
 */
class CsvFormatTest {

    @Test
    fun `the header is the agreed column order`() {
        assertEquals(
            "Date,First Check In,Last Check Out,Total Hours,Session Count,Auto Closed Sessions," +
                "In App Check Outs,Timer Notification Check Outs,Reminder Notification Check Outs\n",
            csvHeader(),
        )
    }

    @Test
    fun `each ending gets its own appended count and the four sum to the session count`() {
        val summary = DailyAggregate(
            dateKey = "2026-06-15",
            totalDurationMs = 2 * 3_600_000L,
            sessionCount = 3,
            firstCheckIn = 0L,
            lastCheckOut = 0L,
            autoClosedSessions = 1,
            inAppCheckOuts = 1,
            timerNotificationCheckOuts = 1,
            reminderNotificationCheckOuts = 0,
        )

        val fields = csvRow("2026-06-15", summary).trimEnd('\n').split(",")

        assertEquals(9, fields.size)
        assertEquals("2026-06-15", fields[0])
        assertEquals("2.00", fields[3])
        assertEquals("3", fields[4])
        // A day worked in three blocks — one closed from the screen, one from the timer
        // notification, one by midnight. The columns count sessions, not days, and say nothing
        // about the day itself.
        assertEquals("1", fields[5])
        assertEquals("1", fields[6])
        assertEquals("1", fields[7])
        assertEquals("0", fields[8])
        // The four endings account for every completed session, since only completed sessions
        // aggregate and each carries exactly one.
        assertEquals(fields[4].toInt(), (5..8).sumOf { fields[it].toInt() })
    }

    /** A gap-filled day states zeros rather than blanks, every ending column included. */
    @Test
    fun `a day with no sessions writes zeros in every count`() {
        val fields = csvRow("2026-06-16", null).trimEnd('\n').split(",")

        assertEquals(9, fields.size)
        assertEquals("0.00", fields[3])
        (4..8).forEach { assertEquals("0", fields[it]) }
    }
}
