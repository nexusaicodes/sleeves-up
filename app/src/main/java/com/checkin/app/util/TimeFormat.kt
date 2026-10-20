package com.checkin.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
private const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE

/**
 * Single source of truth for time/duration formatting used across service, view-models and screens.
 *
 * **`date_key` is deliberately not formatted here, and stays raw ISO wherever it is stored or
 * queried.** `CheckInSessionDao`'s range queries depend on its lexicographic ordering and the CSV
 * `Date` column is machine-read. The rule that follows: **every file that produces or parses a
 * `date_key` keeps its own private `ISO_LOCAL_DATE`, and none of them is drift to be consolidated
 * here** — there are around ten, across `data/`, `service/`, `platform/`, `notify/` and `ui/`,
 * and they are machine-facing to a one. Only the display side comes here, so the test to apply is
 * whether a user reads the result, not whether the code formats a date. [dateKeyWithWeekday] is
 * the entry point that takes a `date_key` and returns something a user reads.
 *
 * **Every function takes a [TimePatterns], and none defaults it.** The unit letters and each date's
 * field order belong to the reader's language, so they are string resources, handed in by
 * `LocalTimePatterns` on screen; a default would let a call site compile in English and render it
 * under Arabic with nothing failing. Digits are Latin in every language — `java.time` prints ASCII
 * digits whatever the locale, and the duration templates are filled under [Locale.ROOT].
 */
object TimeFormat {

    /**
     * Live elapsed for a running clock: "0m 0s" through "59m 59s", then "1h 0m" onward. Seconds are
     * what make a just-started session visibly move; past the hour they are noise, and the minute is
     * the unit everything else in the app reports in.
     */
    fun durationLive(millis: Long, patterns: TimePatterns): String {
        val totalSeconds = millis.coerceAtLeast(0L) / MILLIS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        return if (hours > 0) {
            fill(patterns.hoursMinutes, hours, (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
        } else {
            fill(patterns.minutesSeconds, totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
        }
    }

    /** Compact duration as "Hh Mm" (e.g. a daily total). */
    fun durationShort(millis: Long, patterns: TimePatterns): String {
        val totalMinutes = millis / MILLIS_PER_MINUTE
        return fill(patterns.hoursMinutes, totalMinutes / MINUTES_PER_HOUR, totalMinutes % MINUTES_PER_HOUR)
    }

    /** Wall-clock time of an epoch-millis instant in the device zone (e.g. "09:05 am"). */
    fun clock(epochMillis: Long, patterns: TimePatterns): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(patterns.clockFormatter)

    /** A local minute of the day, 0-1439, in the same form as [clock] (e.g. 615 is "10:15 am"). */
    fun timeOfDay(minuteOfDay: Int, patterns: TimePatterns): String =
        LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE).format(patterns.clockFormatter)

    /** A date the user reads in isolation, so it carries the year (e.g. "25 Jul 2026"). */
    fun dateWithYear(date: LocalDate, patterns: TimePatterns): String = date.format(patterns.dateWithYearFormatter)

    /**
     * [date] already framed by its surroundings — a heading over that day's sessions — so the year
     * is dropped and the weekday earns the space instead (e.g. "Saturday 25 Jul").
     *
     * Private because the screens hold a `date_key`, never a `LocalDate`: [dateKeyWithWeekday] is the
     * way in, and it is the parse this keeps out of the composables that makes it worth having.
     */
    private fun dateWithWeekday(date: LocalDate, patterns: TimePatterns): String =
        date.format(patterns.dateWithWeekdayFormatter)

    /**
     * [dateWithWeekday] over a stored `date_key`, returning null for anything unparseable.
     *
     * `date_key` is an internal ISO string, and the screens that hold one hold it as a nullable
     * selection. Parsing here keeps `LocalDate.parse` out of the composables and means a malformed
     * key degrades to no heading rather than crashing the screen it heads.
     */
    fun dateKeyWithWeekday(dateKey: String?, patterns: TimePatterns): String? = dateKey
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { dateWithWeekday(it, patterns) }

    /** A day under a chart axis (e.g. "25 Jul"). The chart's own title supplies the year. */
    fun axisDay(date: LocalDate, patterns: TimePatterns): String = date.format(patterns.axisDayFormatter)

    /**
     * A month under a chart axis, carrying the year only when the series spans more than one.
     *
     * A bare "MMM" is what a month axis wants — three characters under a bar — and it is a lie the
     * moment the record is long enough to hold two Augusts, which is exactly the series the all-time
     * scope draws. [multiYear] is the series' own question, asked once by the caller rather than per
     * label, so every bar in one chart is formatted the same way.
     */
    fun axisMonth(month: YearMonth, multiYear: Boolean, patterns: TimePatterns): String =
        month.format(if (multiYear) patterns.axisMonthWithYearFormatter else patterns.axisMonthFormatter)

    /**
     * A month heading a user reads in full (e.g. "August 2026").
     *
     * Spelled out and always carrying the year, unlike [axisMonth], because this is a heading read
     * on its own rather than a label under a bar with a title above it. The History month selector and
     * the Reports scope label both show it, and come from here so they cannot disagree.
     */
    fun monthYear(month: YearMonth, patterns: TimePatterns): String = month.format(patterns.monthYearFormatter)

    private fun fill(template: String, first: Long, second: Long): String =
        String.format(Locale.ROOT, template, first, second)
}

/**
 * The language-owned half of [TimeFormat]: unit templates and date patterns, read from string
 * resources so each language sets its own field order and unit letters, plus the [locale] month and
 * weekday names are spelled in.
 *
 * The formatters are built once per instance rather than per call, since a screen formats dozens of
 * dates per frame and an instance lives as long as the configuration it was read from.
 */
// One parameter per string resource: each is a separate translation, and grouping them would only
// rename the same nine values.
@Suppress("LongParameterList")
class TimePatterns(
    val locale: Locale,
    /** Two `%d` slots, hours then minutes (e.g. "%1$dh %2$dm"). */
    val hoursMinutes: String,
    /** Two `%d` slots, minutes then seconds. */
    val minutesSeconds: String,
    clock: String,
    dateWithYear: String,
    dateWithWeekday: String,
    axisDay: String,
    axisMonth: String,
    axisMonthWithYear: String,
    monthYear: String,
) {
    internal val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(clock, locale)
    internal val dateWithYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateWithYear, locale)
    internal val dateWithWeekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateWithWeekday, locale)
    internal val axisDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(axisDay, locale)
    internal val axisMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(axisMonth, locale)
    internal val axisMonthWithYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(axisMonthWithYear, locale)
    internal val monthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(monthYear, locale)
}
