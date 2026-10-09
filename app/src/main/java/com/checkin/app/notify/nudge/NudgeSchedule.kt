package com.checkin.app.notify.nudge

import java.time.Instant
import java.time.ZoneId

/**
 * The times of day a check-in nudge may be sent, and the pure maths for finding the next one.
 *
 * This is the single source of truth for "when is a checkpoint", read by both [NudgeAlarms] (which
 * arms the next one) and [NudgeEligibility] (which decides whether the current hour is inside one).
 * Two copies of these hours is how the alarm and the rule would come to disagree — the alarm firing
 * at an instant the rule then declines to act on, which is silence with nothing to point at.
 *
 * Every function takes the user's chosen first hour as a parameter rather than reading it, so this
 * stays pure, like [com.checkin.app.service.SessionSchedule]: `java.time` only, no clock, no
 * `Context`, and the zone threaded through explicitly. The stored value lives in [ReminderSettings],
 * which is also where it is clamped: every function here takes the hour as already valid.
 */
object NudgeSchedule {

    /** The first reminder's hour before the user has chosen one. */
    const val DEFAULT_FIRST_HOUR = 10

    /** How long after the first reminder the follow-up is due. */
    const val FOLLOW_UP_OFFSET_HOURS = 2

    private const val EARLIEST_FIRST_HOUR = 5
    private const val HOURS_PER_DAY = 24

    /**
     * How much of the day the follow-up band must keep. An inexact alarm may land up to an hour after
     * its trigger, and a nudge held back by [NudgeConfig.minGapMs] lands later still, so a band only
     * an hour wide would leave either one nowhere to send — after midnight no band matches at all.
     */
    private const val MIN_FOLLOW_UP_BAND_HOURS = 2
    private const val LATEST_FIRST_HOUR = HOURS_PER_DAY - MIN_FOLLOW_UP_BAND_HOURS - FOLLOW_UP_OFFSET_HOURS

    /**
     * The hours a user may choose. The upper end keeps the whole follow-up band inside the same
     * calendar day: a follow-up past midnight would ask about a day that has already closed.
     */
    val firstHourRange: IntRange = EARLIEST_FIRST_HOUR..LATEST_FIRST_HOUR

    fun clampFirstHour(hour: Int): Int = hour.coerceIn(firstHourRange)

    fun followUpHour(firstHour: Int): Int = firstHour + FOLLOW_UP_OFFSET_HOURS

    /**
     * The checkpoints, in the order they occur: the chosen hour, then a follow-up in case the day
     * got away from the user.
     *
     * Two rather than one because a single threshold is only ever asked at whatever moment the
     * trigger happens to fire; a second band means a delivery that slips still lands inside one.
     * Copy names the user's chosen hour only as "around" it — delivery is inexact, so stating a time
     * outright promises a punctuality the app cannot keep.
     */
    enum class Checkpoint { FIRST, FOLLOW_UP }

    /**
     * Which checkpoint the local hour [hourOfDay] falls in for a user whose first reminder is at
     * [firstHour], or null before it.
     *
     * **Bands, not thresholds, and that is load-bearing.** [NudgeEligibility] takes the first
     * matching nudge in declaration order, so a `hour >= checkpoint` test would make every hour past
     * the first match FIRST and the follow-up would be unreachable for good. FIRST runs from its hour
     * up to the follow-up, and FOLLOW_UP runs to the end of the day; the hours before the first
     * return null, which is what keeps a nudge out of the small hours without a separate quiet-hours
     * mechanism.
     */
    fun checkpointAt(hourOfDay: Int, firstHour: Int): Checkpoint? = when {
        hourOfDay >= followUpHour(firstHour) -> Checkpoint.FOLLOW_UP
        hourOfDay >= firstHour -> Checkpoint.FIRST
        else -> null
    }

    /**
     * The next checkpoint instant strictly after [fromMs], rolling to tomorrow's first once the day's
     * follow-up has passed.
     *
     * Strictly after, so re-arming from inside the receiver that just fired cannot schedule the same
     * instant again and spin. It goes through the calendar (`atTime(...).atZone(zone)`) rather than
     * adding hours to an epoch, so a DST day still puts each checkpoint at its stated local hour.
     */
    fun nextCheckpointAfter(fromMs: Long, zone: ZoneId, firstHour: Int): Long {
        val today = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        return listOf(firstHour, followUpHour(firstHour))
            .map { today.atTime(it, 0).atZone(zone).toInstant().toEpochMilli() }
            .firstOrNull { it > fromMs }
            ?: today.plusDays(1).atTime(firstHour, 0).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * The instant the alarm should be armed for: the next checkpoint, or [retryAtMs] when that comes
     * sooner — the moment [NudgeConfig.minGapMs] stops holding back a nudge that is otherwise due.
     *
     * Without the retry, a gap-blocked follow-up waits for tomorrow's first checkpoint and is sent only
     * if the hourly worker happens to run in between — which, on the device this matters for, it
     * does not. A retry at or before [fromMs] is ignored, so it can never re-arm an instant already
     * passed and spin.
     */
    fun nextAlarmAt(fromMs: Long, zone: ZoneId, firstHour: Int, retryAtMs: Long?): Long {
        val next = nextCheckpointAfter(fromMs, zone, firstHour)
        return if (retryAtMs != null && retryAtMs in (fromMs + 1) until next) retryAtMs else next
    }
}
