package com.checkin.app.data

import com.checkin.app.data.local.DailyAggregate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pure statistics over a date-keyed map of days that had sessions.
 *
 * Showing up is the unit. A day counts because it has an entry, not because its hours cleared a
 * bar — so a 45-minute day on a bad week counts exactly as much as a nine-hour one.
 *
 * **Hours are carried, never ranked.** Nothing here returns a longest day, a personal best or a run
 * length, because every one of those is a baseline something else can then be measured against, and
 * a measured quantity is a graded one. [totalWorkedMs] is a sum and is meant to be read as a sum.
 */
object ConsistencyStats {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun showedUp(summaries: Map<String, DailyAggregate>, date: LocalDate): Boolean =
        summaries.containsKey(date.format(dateFormatter))

    /**
     * The last day that counts: [today] once it holds a completed session, otherwise yesterday.
     *
     * Every window in the app ends here, so a day joins the counts and the calendar the moment it is
     * checked out rather than at the next midnight.
     *
     * Conditional on purpose. A window that always ended at [today] would open every morning with a
     * missed day and a dipped count, then un-report both at the first check-out — the failure-first
     * reading this app has no room for. Ending at yesterday until today has produced something means
     * the numbers only ever move upward during a day.
     *
     * The test is free because the aggregate queries keep only completed sessions: a key for [today]
     * in [summaries] *is* "today has ended a session", so an open one correctly counts for nothing.
     * It follows that [summaries] can hold no key later than the returned day, which is why
     * [totalWorkedMs] and [showedUpDays] need no range of their own.
     */
    fun countedThrough(summaries: Map<String, DailyAggregate>, today: LocalDate): LocalDate =
        if (showedUp(summaries, today)) today else today.minusDays(1)

    fun showedUpDays(summaries: Map<String, DailyAggregate>): Int = summaries.size

    fun totalWorkedMs(summaries: Map<String, DailyAggregate>): Long = summaries.values.sumOf { it.totalDurationMs }

    /** Completed sessions across [summaries], however they are spread over the days. */
    fun totalSessions(summaries: Map<String, DailyAggregate>): Int = summaries.values.sumOf { it.sessionCount }
}
