package com.checkin.app.data.local

/**
 * One day's completed sessions, rolled up. Room aggregate query result — not an entity.
 *
 * There is deliberately no status alongside it: a day that has an entry here is a day the user
 * showed up, one that has none is a day they didn't, and the hours are carried without being graded.
 * See [com.checkin.app.data.ConsistencyStats] for why.
 */
data class DailyAggregate(
    val dateKey: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstCheckIn: Long,
    val lastCheckOut: Long?,
    /** How many of the day's sessions the day-boundary alarm closed. Exported; never displayed. */
    val autoClosedSessions: Int,
)
