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
    /**
     * How many of the day's sessions each ending accounts for. Exported; **never displayed**, and
     * never compared to each other in front of the user — see [ClosedBy].
     *
     * Counts rather than values because these rows are days: a day worked in three blocks, two
     * closed from the screen and one by midnight, reports 2 and 1. They sum to [sessionCount], since
     * only completed sessions aggregate and every completed session carries an ending.
     */
    val dayBoundaryCheckOuts: Int,
    val inAppCheckOuts: Int,
    val timerNotificationCheckOuts: Int,
    val reminderNotificationCheckOuts: Int,
)
