package com.checkin.app.notify.nudge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a habit's check-in nudge may be sent, and the pure maths for finding the next instant.
 *
 * This is the single source of truth for "when is a habit's band", read by [NudgeEligibility] both to
 * decide whether the current minute is inside one and to work out when the alarm should next fire.
 * Two copies of these times is how the alarm and the rule would come to disagree — the alarm firing
 * at an instant the rule then declines to act on, which is silence with nothing to point at.
 *
 * A reminder time is a **minute of the day** (0–1439, local), so 10:15 is 615. Every function takes
 * the habit's chosen minute as a parameter rather than reading it, so this stays pure, like
 * [com.checkin.app.service.SessionSchedule]: `java.time` only, no clock, no `Context`, and the zone
 * threaded through explicitly. The stored value lives in [ReminderSettings], which is also where it
 * is clamped: every function here takes the minute as already valid.
 */
object NudgeSchedule {

    private const val MINUTES_PER_HOUR = 60
    private const val HOURS_PER_DAY = 24

    /** A habit's reminder time before one has been chosen for it: 10:00. */
    const val DEFAULT_MINUTE = 10 * MINUTES_PER_HOUR

    private const val EARLIEST_HOUR = 5

    /**
     * How much of the day a band must keep before midnight. An inexact alarm may land up to an hour
     * after its trigger, and a habit whose band opens just after another habit's reminder went out
     * is held back by [NudgeConfig.minGapMs] — ninety minutes — on top of that. A band narrower than
     * the two together could close before either delivery lands, and after midnight the question
     * is about a day that has already ended.
     */
    private const val MIN_BAND_HOURS = 3
    private const val LATEST_HOUR = HOURS_PER_DAY - MIN_BAND_HOURS

    /** The minutes of the day a user may choose for a habit: 05:00 to 21:00, both included. */
    val minuteRange: IntRange = EARLIEST_HOUR * MINUTES_PER_HOUR..LATEST_HOUR * MINUTES_PER_HOUR

    fun clampMinute(minute: Int): Int = minute.coerceIn(minuteRange)

    /**
     * Whether the local [minuteOfDay] is inside the band of a habit reminded at [reminderMinute]:
     * from that minute to the end of the day. The time before it is outside, which is what keeps a
     * nudge out of the small hours without a separate quiet-hours mechanism.
     *
     * One band running to midnight rather than a threshold asked at one moment, because the trigger
     * is inexact: a delivery that slips still lands inside it. Copy names the time only as "around"
     * it for the same reason.
     */
    fun inBand(minuteOfDay: Int, reminderMinute: Int): Boolean = minuteOfDay >= reminderMinute

    /**
     * The next band opening strictly after [fromMs] — today's if it is still ahead, otherwise
     * tomorrow's.
     *
     * This is the fallback arm, used only when the state [NudgeEligibility.nextEligibleAt] needs
     * cannot be read: it knows nothing of what was sent, so it skips a band that is open but unsent.
     * Strictly after, so it can never re-arm the instant that just fired and spin. It reads the
     * instants off [bandOn], so the fallback and the rules cannot disagree about where a band opens.
     */
    fun nextBandAfter(fromMs: Long, zone: ZoneId, reminderMinute: Int): Long {
        val today = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        return bandOn(today, zone, reminderMinute).first.takeIf { it > fromMs }
            ?: bandOn(today.plusDays(1), zone, reminderMinute).first
    }

    /**
     * The instants a habit's band covers on [date]: from its minute up to midnight. The same band
     * [inBand] reads by minute, as instants, for asking *when* it opens rather than *whether* it is
     * open now. It goes through the calendar (`atTime(...).atZone(zone)`) rather than adding minutes
     * to an epoch, so a DST day still opens the band at its stated local time.
     */
    fun bandOn(date: LocalDate, zone: ZoneId, reminderMinute: Int): LongRange =
        date.atTime(reminderMinute / MINUTES_PER_HOUR, reminderMinute % MINUTES_PER_HOUR)
            .atZone(zone).toInstant().toEpochMilli() until
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
