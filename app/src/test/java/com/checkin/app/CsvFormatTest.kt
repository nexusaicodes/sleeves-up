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
            "Date,First Check In,Last Check Out,Total Hours,Session Count,Auto Closed Sessions\n",
            csvHeader(),
        )
    }

    @Test
    fun `the auto-closed count is appended after the existing columns`() {
        val summary = DailyAggregate(
            dateKey = "2026-06-15",
            totalDurationMs = 2 * 3_600_000L,
            sessionCount = 3,
            firstCheckIn = 0L,
            lastCheckOut = 0L,
            autoClosedSessions = 1,
        )

        val fields = csvRow("2026-06-15", summary).trimEnd('\n').split(",")

        assertEquals(6, fields.size)
        assertEquals("2026-06-15", fields[0])
        assertEquals("2.00", fields[3])
        assertEquals("3", fields[4])
        // A day worked in three blocks, one of which midnight closed, reports 1 — the column counts
        // sessions, not days, and says nothing about the day itself.
        assertEquals("1", fields[5])
    }

    /** A gap-filled day states zeros rather than blanks, the new column included. */
    @Test
    fun `a day with no sessions writes zeros in every count`() {
        val fields = csvRow("2026-06-16", null).trimEnd('\n').split(",")

        assertEquals(6, fields.size)
        assertEquals("0.00", fields[3])
        assertEquals("0", fields[4])
        assertEquals("0", fields[5])
    }
}
