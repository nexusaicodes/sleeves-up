package com.checkin.app.platform

import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.util.TimeFormat
import java.util.Locale

/*
 * The CSV's shape, kept apart from the class that writes and shares the file.
 *
 * This is a data contract people script against, and it is pure, so it is the half a JVM test can
 * pin — [DefaultCsvExporter] needs a `Context` and cannot be. Filed under the exporter's name, the
 * frozen column order was three top-level privates inside a file about file I/O and share sheets.
 *
 * What is *not* here is the range: the exporter is handed two `date_key` strings already clamped by
 * `ReportScope.resolve`, so the file and the screen it was exported from cannot describe different
 * days.
 */

/** Decimal hours is the CSV's unit, so the divisor is a Double. */
private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * The column order, which is **append-only**.
 *
 * People write scripts against this file, so an existing column is never renamed, retyped or moved —
 * a new one goes on the end or it does not go in at all.
 *
 * There is deliberately no Status column. Any wording for one would be a verdict on the day, and this
 * is the one artifact that leaves the device — zero hours and zero sessions already say a day held
 * nothing, without grading it. The last four columns are not that column and must not become it:
 * they count what ended each of the day's sessions, which is a fact about what happened to a
 * *session*, not an assessment of the *day*. They are per-day counts because these rows are days; a
 * day worked in three blocks, two of which the user closed themselves, reports 1 and 2.
 *
 * There is one column per [ClosedBy][com.checkin.app.data.local.ClosedBy] value and each is named
 * for the value it counts, so a fifth ending means a tenth column rather than a re-reading of these.
 * The four sum to `Session Count`.
 */
private const val CSV_HEADER = "Date,First Check In,Last Check Out,Total Hours,Session Count," +
    "Day Boundary Check Outs,In App Check Outs,Timer Notification Check Outs," +
    "Reminder Notification Check Outs\n"

/**
 * One day's row, or a gap-filled day of zeros when [summary] is null.
 *
 * Top-level and pure so the column order can be pinned by a JVM test — [DefaultCsvExporter] needs a
 * `Context` and cannot be, which is how a header could otherwise be reordered with nothing failing.
 */
internal fun csvRow(key: String, summary: DailyAggregate?): String {
    val firstIn = summary?.firstCheckIn?.let { TimeFormat.clock(it) } ?: ""
    val lastOut = summary?.lastCheckOut?.let { TimeFormat.clock(it) } ?: ""
    val totalHrs = summary
        ?.let { String.format(Locale.US, "%.2f", it.totalDurationMs / MILLIS_PER_HOUR) }
        ?: "0.00"
    val count = summary?.sessionCount?.toString() ?: "0"
    val dayBoundary = summary?.dayBoundaryCheckOuts?.toString() ?: "0"
    val inApp = summary?.inAppCheckOuts?.toString() ?: "0"
    val fromTimer = summary?.timerNotificationCheckOuts?.toString() ?: "0"
    val fromReminder = summary?.reminderNotificationCheckOuts?.toString() ?: "0"
    return "$key,$firstIn,$lastOut,$totalHrs,$count,$dayBoundary,$inApp,$fromTimer,$fromReminder\n"
}

/**
 * The header line. The one way to read [CSV_HEADER] — the exporter writes through this too, so the
 * bytes a test pins and the bytes a file gets are the same expression.
 */
internal fun csvHeader(): String = CSV_HEADER
