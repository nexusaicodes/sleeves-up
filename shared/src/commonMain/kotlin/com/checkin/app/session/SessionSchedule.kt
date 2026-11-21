package com.checkin.app.session

import com.checkin.app.util.MILLIS_PER_MINUTE
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Pure scheduling math for the two things that happen to a session while it runs: the periodic
 * "still going?" reminder, and the day boundary that closes it.
 *
 * The cadence is a plain interval, deliberately not randomized: nothing here verifies anything, so
 * there is no check for a predictable time to defeat.
 */
object SessionSchedule {

    /**
     * How often an open session asks whether it is still open.
     *
     * Two hours: close enough together that a session forgotten in the morning is caught in the
     * afternoon, far enough apart that one deliberately left running is not harassed. Only the first
     * of a session alerts — see `SessionLifecycle.onReminderFired`.
     */
    const val REMINDER_INTERVAL_MS = 120L * MILLIS_PER_MINUTE

    /** The next reminder instant, [REMINDER_INTERVAL_MS] after [fromMs]. */
    fun nextReminderAt(fromMs: Long): Long = fromMs + REMINDER_INTERVAL_MS

    /**
     * The local midnight that ends the day [atMs] falls in — strictly after [atMs], so an instant
     * that is *already* midnight gets the following one rather than closing a session the moment it
     * opens.
     */
    fun nextDayBoundaryAfter(atMs: Long, zone: TimeZone): Long =
        Instant.fromEpochMilliseconds(atMs).toLocalDateTime(zone).date.dayAfterStartIn(zone)

    /**
     * The local midnight that ends the day named by an ISO `date_key`.
     *
     * Derived from the session's own day rather than from when the alarm fired, since an inexact
     * alarm may land hours late (see `SessionLifecycle.onDayBoundaryFired`). Returns null on a
     * malformed key rather than throwing, matching how the rest of the app treats `date_key`.
     */
    @Suppress("SwallowedException")
    fun dayBoundaryOf(dateKey: String, zone: TimeZone): Long? = try {
        LocalDate.parse(dateKey).dayAfterStartIn(zone)
    } catch (e: IllegalArgumentException) {
        // kotlinx-datetime's parse failure, DateTimeFormatException, is an IllegalArgumentException.
        null
    }

    /**
     * The first instant of the day after this date in [zone], in epoch millis. Through the calendar,
     * not a fixed 24 hours, so a DST day still ends at its own midnight; where midnight itself falls in
     * a DST gap, this is the first instant that exists after it.
     */
    private fun LocalDate.dayAfterStartIn(zone: TimeZone): Long =
        plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
}
