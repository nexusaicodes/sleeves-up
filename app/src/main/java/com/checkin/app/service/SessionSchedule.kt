package com.checkin.app.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Pure scheduling math for the two things that happen to a session while it runs: the periodic
 * "still going?" reminder, and the day boundary that closes it.
 *
 * The cadence is a plain interval, deliberately not randomized: nothing here verifies anything, so
 * there is no check for a predictable time to defeat. Neither instant derives from a daily target —
 * there is none.
 *
 * ### Reading the `service` package
 *
 * Every file here is about a session, so the `Session` prefix discriminates nothing — what separates
 * them is which of three layers a file is on, and the names do not say. They are:
 *
 * - **Pure math**, no Android, unit-tested: this file (cadence and the two midnight derivations) and
 *   [SessionClock] (the notification's chronometer base and elapsed time). Neither schedules
 *   anything; both are asked what an instant should be.
 * - **Platform seam**, an interface plus its Android implementation: [SessionAlarms], which is what
 *   actually reaches `AlarmManager`, and which persists the armed instants and the reminder count.
 * - **Orchestration**, where the decisions live: [SessionReminderRunner] (arms both alarms, handles
 *   the reminder, **and closes the session at the day boundary** — the app's only un-gated
 *   check-out), [SessionWatchdog] (repairs a service or alarms lost to a kill),
 *   [ServiceReconciler] (whether a live service still has a row behind it).
 *
 * Plus the two receivers ([SessionAlarmReceiver], [SessionRestoreReceiver]) and [CheckInService]
 * itself, which owns the ongoing notification and nothing else — it runs no ticker and arms no
 * alarm.
 */
object SessionSchedule {

    private const val MINUTE_MS = 60L * 1_000L

    /**
     * How often an open session asks whether it is still open.
     *
     * Two hours: close enough together that a session forgotten in the morning is caught in the
     * afternoon, far enough apart that one deliberately left running is not harassed. Only the first
     * reminder of a session alerts; the rest post silently, so a session running overnight
     * accumulates on the shade instead of buzzing every two hours until dawn.
     */
    const val REMINDER_INTERVAL_MS = 120L * MINUTE_MS

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** The next reminder instant, [REMINDER_INTERVAL_MS] after [fromMs]. */
    fun nextReminderAt(fromMs: Long): Long = fromMs + REMINDER_INTERVAL_MS

    /**
     * The local midnight that ends the day [atMs] falls in — strictly after [atMs], so an instant
     * that is *already* midnight gets the following one rather than closing a session the moment it
     * opens.
     */
    fun nextDayBoundaryAfter(atMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(atMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * The local midnight that ends the day named by an ISO `date_key`.
     *
     * The closing instant is derived from the session's own day rather than from when the alarm
     * happened to fire. An inexact alarm is allowed to land late — hours late, if the device was in
     * Doze — and stamping a check-out at the fire time would credit a forgotten session with hours
     * on a day it does not belong to. Returns null on a malformed key rather than throwing, matching
     * how the rest of the app treats the nullable `date_key` it holds.
     */
    @Suppress("SwallowedException")
    fun dayBoundaryOf(dateKey: String, zone: ZoneId): Long? = try {
        LocalDate.parse(dateKey, dateFormatter).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    } catch (e: java.time.format.DateTimeParseException) {
        null
    }
}
