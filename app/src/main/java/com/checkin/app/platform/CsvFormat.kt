package com.checkin.app.platform

import com.checkin.app.data.local.DailyAggregate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The CSV's shape, kept apart from the class that writes and shares the file.
 *
 * This is a data contract people script against, and it is pure, so it is the half a JVM test can
 * pin — [DefaultCsvExporter] needs a `Context` and cannot be. The frozen column order belongs with
 * the functions that spell it, not inside a file about file I/O and share sheets.
 *
 * What is *not* here is the range: the exporter is handed two `date_key` strings already clamped by
 * `ReportScope.resolve`, so the file and the screen it was exported from cannot describe different
 * days.
 */

/**
 * The clock columns' own formatter, pinned to [Locale.US] and kept out of `TimeFormat`: the screens'
 * clock follows the reader's language, and this file is machine-read, so it must read the same bytes
 * ("09:05 AM") whatever language the app is in when it is exported.
 */
private val CSV_CLOCK = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

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
    val firstIn = summary?.firstCheckIn?.let(::csvClock) ?: ""
    val lastOut = summary?.lastCheckOut?.let(::csvClock) ?: ""
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

private fun csvClock(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(CSV_CLOCK)

/**
 * The header line. The one way to read [CSV_HEADER] — the exporter writes through this too, so the
 * bytes a test pins and the bytes a file gets are the same expression.
 */
internal fun csvHeader(): String = CSV_HEADER

/**
 * The exported file's name: `checkin_<habit>_<start>_<end>.csv`, the habit reduced to letters,
 * their combining marks, digits, `-` and `_` so no name can put a path separator or a character a share target rejects into
 * it. A habit still owed a name, or one with nothing left after the reduction, is `habit` — the file
 * is still that habit's alone, it just cannot say so.
 */
internal fun exportFileName(habitName: String?, startKey: String, endKey: String): String {
    val slug = habitName.orEmpty()
        .replace(FILE_NAME_UNSAFE, "_")
        .trim('_')
        .lowercase(Locale.ROOT)
        .ifEmpty { "habit" }
    return "checkin_${slug}_${startKey}_$endKey.csv"
}

// `\p{M}` keeps a letter's combining marks with it: Arabic harakat and decomposed Latin accents are
// marks rather than letters, and without it each one would split a name into `_`-joined pieces.
private val FILE_NAME_UNSAFE = Regex("[^\\p{L}\\p{M}\\p{N}_-]+")
