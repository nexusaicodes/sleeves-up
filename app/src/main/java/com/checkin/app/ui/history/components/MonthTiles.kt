package com.checkin.app.ui.history.components

import com.checkin.app.data.ConsistencyStats
import com.checkin.app.data.SessionRhythm
import com.checkin.app.data.local.DailyAggregate

/** Month-summary values over the month's counted days. See [computeMonthTiles]. */
data class MonthTiles(
    val showedUp: Int,
    val missed: Int,
    /** Worked time per tracked day. A quantity the card prints; nothing measures it against anything. */
    val avgDailyMs: Long,
    /** Mean sessions per day shown up — how the days were shaped, not how much they held. */
    val avgSessionsPerDay: Float,
)

/**
 * Tile values for the month card, over the [trackedDaysInMonth] days of it that count.
 *
 * [summaries] needs no filtering of its own: the aggregate queries keep only completed sessions, so
 * an in-progress today is absent from the map exactly as long as it is absent from
 * [trackedDaysInMonth] — the caller derives both from `ConsistencyStats.countedThrough`, which is
 * what stops the two disagreeing about whether today is in the month.
 *
 * [MonthTiles.missed] is derived by subtraction, so a tracked day with no sessions is a day not shown
 * up for; it is carried for the screen-reader description and is deliberately not on the face of the
 * card. The daily average divides by [trackedDaysInMonth] rather than by the days that had sessions,
 * which keeps missed days in the denominator; [MonthTiles.avgSessionsPerDay] divides the other way on
 * purpose — see `SessionRhythm.averageSessionsPerDay`.
 *
 * **No figure here is a baseline.** The month's longest day and its best run of consecutive days were
 * both computed here and both are gone: each existed only to be the denominator of a ring, and a ring
 * filled against the user's own record grades every ordinary month against their best one.
 */
fun computeMonthTiles(summaries: Map<String, DailyAggregate>, trackedDaysInMonth: Int): MonthTiles {
    val showedUp = summaries.size
    val missed = (trackedDaysInMonth - showedUp).coerceAtLeast(0)
    val totalHoursMs = ConsistencyStats.totalWorkedMs(summaries)
    val avgDailyMs = if (trackedDaysInMonth > 0) totalHoursMs / trackedDaysInMonth else 0L
    return MonthTiles(showedUp, missed, avgDailyMs, SessionRhythm.averageSessionsPerDay(summaries))
}

/**
 * How full the showed-up ring reads: days with a session as a share of the month's tracked days.
 *
 * This is the **one** fill left in the app, and what makes it legitimate is the denominator. It is
 * the month itself — a bounded, externally-fixed number of days — not a figure the user previously
 * achieved. A ring measured against a personal best ratchets: beating it once re-renders every month
 * behind it as a partial version of that one. Days shown up is the app's thesis and is allowed a
 * direction; hours are not, and no overload of this exists for them.
 *
 * A zero or absent denominator gives an empty ring rather than a division by zero, which is also the
 * honest reading for a month with nothing tracked in it yet.
 */
fun showedUpRatio(showedUp: Int, trackedDays: Int): Float =
    if (trackedDays <= 0) 0f else (showedUp.toFloat() / trackedDays.toFloat()).coerceIn(0f, 1f)
