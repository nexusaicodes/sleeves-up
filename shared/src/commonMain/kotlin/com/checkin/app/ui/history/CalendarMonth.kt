package com.checkin.app.ui.history

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * One day of the month the History calendar draws, in the only states a day can take there.
 *
 * **[recorded] is binary and must stay that way.** A shade scaled by a day's hours against the
 * longest day renders every ordinary day as a partial version of the best one, and a single long day
 * re-grades the whole history behind it. A 45-minute day and a nine-hour day are the same mark.
 */
data class CalendarDay(
    val date: LocalDate,
    /** The `date_key` the day's sessions carry. */
    val dateKey: String,
    /** Shaded: the day holds a completed session and lies inside the counted record. */
    val recorded: Boolean,
    /**
     * Faded: a day the record does not cover — the future, a day before the first session, or every
     * day while there is none. Never an empty day inside the record: drawing a day fainter for
     * holding nothing is a verdict this calendar does not render.
     */
    val outsideRecord: Boolean,
    /** Carries the today marker, whether or not it has been checked out of yet. */
    val today: Boolean,
)

/** The History calendar's geometry and day states, for both apps' grids. */
object CalendarMonth {

    const val DAYS_PER_WEEK = 7

    /** The column [month]'s first day falls into, with weeks starting on [firstDayOfWeek]. */
    fun leadingBlanks(month: YearMonth, firstDayOfWeek: DayOfWeek): Int =
        (month.firstDay.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + DAYS_PER_WEEK) % DAYS_PER_WEEK

    /**
     * Week rows [month] occupies, 4 to 6; a partial trailing week still takes a row. A screen sizing
     * its cells before the grid is laid out asks here, so both count the same rows.
     */
    fun weekRows(month: YearMonth, firstDayOfWeek: DayOfWeek): Int =
        (leadingBlanks(month, firstDayOfWeek) + month.numberOfDays + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK

    /**
     * Every day of [month] in order.
     *
     * @param recordedKeys the `date_key`s holding a completed session.
     * @param trackingStart the first session's day, or null while there is none.
     * @param today the day carrying the marker and the first day of the future.
     * @param countedThrough the last day that shades: today once it has been checked out of,
     *   otherwise yesterday (`ConsistencyStats.countedThrough`), so a day takes its shade at check-out.
     */
    fun days(
        month: YearMonth,
        recordedKeys: Set<String>,
        trackingStart: LocalDate?,
        today: LocalDate,
        countedThrough: LocalDate,
    ): List<CalendarDay> = (0 until month.numberOfDays).map { offset ->
        val date = month.firstDay.plus(offset, DateTimeUnit.DAY)
        val key = date.toString()
        val counted = trackingStart != null && date >= trackingStart && date <= countedThrough
        CalendarDay(
            date = date,
            dateKey = key,
            recorded = counted && key in recordedKeys,
            outsideRecord = trackingStart == null || date < trackingStart || date > today,
            today = date == today,
        )
    }
}
