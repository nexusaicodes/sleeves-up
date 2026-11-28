package com.checkin.app.platform

import com.checkin.app.data.local.DailyAggregate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * The CSV's shape, kept apart from the class that writes and shares the file.
 *
 * This is a data contract people script against, and it is pure, so it is the half a test can pin
 * on both platforms — the Android app's `AndroidCsvExporter` needs a `Context` and cannot be. The
 * frozen column order belongs with the functions that spell it, not inside a file about file I/O and
 * share sheets.
 *
 * What is *not* decided here is the range: [csvLines] is handed two `date_key` strings already
 * clamped by `ReportScope.resolve`, so the file and the screen it was exported from cannot describe
 * different days.
 */

/** Milliseconds in a hundredth of an hour, the precision of the Total Hours column. */
private const val MILLIS_PER_HUNDREDTH_HOUR = 36_000L

private const val HOURS_PER_HALF_DAY = 12
private const val PERCENT = 100

/**
 * The column order, which is **append-only**.
 *
 * People write scripts against this file, so an existing column is never renamed, retyped or moved —
 * a new one goes on the end or it does not go in at all.
 *
 * There is deliberately no Status column. Any wording for one would be a verdict on the day, and this
 * is the one artifact the user hands to someone else — zero hours and zero sessions already say a day held
 * nothing, without grading it. The last five columns are not that column and must not become it:
 * they count what ended each of the day's sessions, which is a fact about what happened to a
 * *session*, not an assessment of the *day*. They are per-day counts because these rows are days; a
 * day worked in three blocks, two of which the user closed themselves, reports 1 and 2.
 *
 * There is one column per [ClosedBy][com.checkin.app.data.local.ClosedBy] value and each is named
 * for the value it counts, so a sixth ending means an eleventh column rather than a re-reading of
 * these. The five sum to `Session Count`.
 */
private const val CSV_HEADER = "Date,First Check In,Last Check Out,Total Hours,Session Count," +
    "Day Boundary Check Outs,In App Check Outs,Timer Notification Check Outs," +
    "Reminder Notification Check Outs,Widget Check Outs\n"

/** One day's row, or a gap-filled day of zeros when [summary] is null. Clocks are read in [zone]. */
fun csvRow(key: String, summary: DailyAggregate?, zone: TimeZone): String {
    val firstIn = summary?.firstCheckIn?.let { csvClock(it, zone) } ?: ""
    val lastOut = summary?.lastCheckOut?.let { csvClock(it, zone) } ?: ""
    val totalHrs = summary?.let { csvHours(it.totalDurationMs) } ?: "0.00"
    val count = summary?.sessionCount?.toString() ?: "0"
    val dayBoundary = summary?.dayBoundaryCheckOuts?.toString() ?: "0"
    val inApp = summary?.inAppCheckOuts?.toString() ?: "0"
    val fromTimer = summary?.timerNotificationCheckOuts?.toString() ?: "0"
    val fromReminder = summary?.reminderNotificationCheckOuts?.toString() ?: "0"
    val fromWidget = summary?.widgetCheckOuts?.toString() ?: "0"
    return "$key,$firstIn,$lastOut,$totalHrs,$count,$dayBoundary,$inApp,$fromTimer,$fromReminder,$fromWidget\n"
}

/**
 * A clock column: "09:05 AM", in ASCII digits and English, whatever language the app is in. Kept out
 * of `TimeFormat`, whose clocks follow the reader's language, because this file is machine-read and
 * must carry the same bytes whoever exports it. It spells out what `hh:mm a` in `Locale.US` writes.
 */
private fun csvClock(epochMillis: Long, zone: TimeZone): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    val hour = (time.hour % HOURS_PER_HALF_DAY).let { if (it == 0) HOURS_PER_HALF_DAY else it }
    val half = if (time.hour < HOURS_PER_HALF_DAY) "AM" else "PM"
    return "${twoDigits(hour)}:${twoDigits(time.minute)} $half"
}

/**
 * Decimal hours to two places, rounded half up as Java's `%.2f` rounds, worked in whole hundredths
 * of an hour so no floating-point step can tip a value across a rounding boundary. A negative
 * duration keeps its sign, `-0.00` included, as `%.2f` writes it; the repository floors durations
 * at zero, so only a corrupt row could reach that.
 */
private fun csvHours(durationMs: Long): String {
    val magnitude = if (durationMs < 0) -durationMs else durationMs
    val hundredths = (magnitude + MILLIS_PER_HUNDREDTH_HOUR / 2) / MILLIS_PER_HUNDREDTH_HOUR
    val sign = if (durationMs < 0) "-" else ""
    return "$sign${hundredths / PERCENT}.${twoDigits((hundredths % PERCENT).toInt())}"
}

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

/**
 * The header line. The one way to read [CSV_HEADER] — the exporter writes through this too, so the
 * bytes a test pins and the bytes a file gets are the same expression.
 */
fun csvHeader(): String = CSV_HEADER

/**
 * Every line of the file, header first, then one row per day from [startKey] to [endKey] inclusive.
 * A day with no summary is a row of zeros, not a missing row: a script reading the file counts
 * days, and a gap would read as a day the export forgot. The keys are `date_key`s (ISO dates), and
 * the clock columns are read in [zone].
 */
fun csvLines(
    startKey: String,
    endKey: String,
    summaries: Map<String, DailyAggregate>,
    zone: TimeZone,
): Sequence<String> {
    val end = LocalDate.parse(endKey)
    val days = generateSequence(LocalDate.parse(startKey)) { it.plus(1, DateTimeUnit.DAY) }
        .takeWhile { it <= end }
        .map { day -> day.toString().let { key -> csvRow(key, summaries[key], zone) } }
    return sequenceOf(csvHeader()) + days
}

/**
 * The exported file's name: `checkin_<habit>_<start>_<end>.csv`, the habit reduced to letters, their
 * combining marks, digits, `-` and `_` so no name can put a path separator, or a character a share
 * target rejects, into it. A habit still owed a name, or one with nothing left after the reduction,
 * is `habit` — the file is still that habit's alone, it just cannot say so.
 */
fun exportFileName(habitName: String?, startKey: String, endKey: String): String {
    val slug = habitName.orEmpty()
        .replace(FILE_NAME_UNSAFE, "_")
        .trim('_')
        .lowercase()
        .ifEmpty { "habit" }
    return "checkin_${slug}_${startKey}_$endKey.csv"
}

// `\p{M}` keeps a letter's combining marks with it: Arabic harakat and decomposed Latin accents are
// marks rather than letters, and without it each one would split a name into `_`-joined pieces.
private val FILE_NAME_UNSAFE = Regex("[^\\p{L}\\p{M}\\p{N}_-]+")
