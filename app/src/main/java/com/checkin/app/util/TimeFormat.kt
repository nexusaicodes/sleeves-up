package com.checkin.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
private const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE

/** Single source of truth for time/duration formatting used across service, view-models and screens. */
object TimeFormat {

    private val clockFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    // Dates the user reads are formatted here and nowhere else — building one at a call site is how
    // a raw ISO `LocalDate.toString()` or a raw `date_key` reaches the screen. Both patterns are the
    // same abbreviated-month family, so a date is recognisably a date wherever it appears; only the
    // parts that earn their space differ.
    private val dateWithYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val dateWithWeekdayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)

    /**
     * Live elapsed for a running clock: "0m 0s" through "59m 59s", then "1h 0m" onward. Seconds are
     * what make a just-started session visibly move; past the hour they are noise, and the minute is
     * the unit everything else in the app reports in.
     */
    fun durationLive(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        return if (hours > 0) {
            "${hours}h ${(totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE}m"
        } else {
            "${totalSeconds / SECONDS_PER_MINUTE}m ${totalSeconds % SECONDS_PER_MINUTE}s"
        }
    }

    /** Compact duration as "Hh Mm" (e.g. a daily total). */
    fun durationShort(millis: Long): String {
        val totalMinutes = millis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return "${hours}h ${minutes}m"
    }

    /** Wall-clock time of an epoch-millis instant in the device zone (e.g. "09:05 AM"). */
    fun clock(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(clockFormatter)

    /** A date the user reads in isolation, so it carries the year (e.g. "Jul 25, 2026"). */
    fun dateWithYear(date: LocalDate): String = date.format(dateWithYearFormatter)

    /**
     * A date already framed by its surroundings — a heading over that day's sessions — so the year
     * is dropped and the weekday earns the space instead (e.g. "Saturday, Jul 25").
     *
     * Private because the screens hold a `date_key`, never a `LocalDate`: [dateKeyWithWeekday] is the
     * way in, and it is the parse this keeps out of the composables that makes it worth having.
     */
    private fun dateWithWeekday(date: LocalDate): String = date.format(dateWithWeekdayFormatter)

    /**
     * [dateWithWeekday] over a stored `date_key`, returning null for anything unparseable.
     *
     * `date_key` is an internal ISO string, and the screens that hold one hold it as a nullable
     * selection. Parsing here keeps `LocalDate.parse` out of the composables and means a malformed
     * key degrades to no heading rather than crashing the screen it heads.
     */
    fun dateKeyWithWeekday(dateKey: String?): String? = dateKey
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let(::dateWithWeekday)
}
