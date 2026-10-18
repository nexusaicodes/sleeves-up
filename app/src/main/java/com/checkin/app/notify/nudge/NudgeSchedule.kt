package com.checkin.app.notify.nudge

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * When a habit's check-in nudge may be sent, and the pure maths for finding the next instant.
 *
 * This is the single source of truth for "when is a habit's band", read by [NudgeEligibility] both to
 * decide whether the current hour is inside one and to work out when the alarm should next fire.
 * Two copies of these hours is how the alarm and the rule would come to disagree — the alarm firing
 * at an instant the rule then declines to act on, which is silence with nothing to point at.
 *
 * Every function takes the habit's chosen hour as a parameter rather than reading it, so this stays
 * pure, like [com.checkin.app.service.SessionSchedule]: `java.time` only, no clock, no `Context`, and
 * the zone threaded through explicitly. The stored value lives in [ReminderSettings], which is also
 * where it is clamped: every function here takes the hour as already valid.
 */
object NudgeSchedule {

    /** A habit's reminder hour before one has been chosen for it. */
    const val DEFAULT_HOUR = 10

    private const val EARLIEST_HOUR = 5
    private const val HOURS_PER_DAY = 24

    /**
     * How much of the day a band must keep before midnight. An inexact alarm may land up to an hour
     * after its trigger, and a habit whose band opens just after another habit's reminder went out
     * is held back by [NudgeConfig.minGapMs] — ninety minutes — on top of that. A band narrower than
     * the two together could close before either delivery lands, and after midnight the question
     * is about a day that has already ended.
     */
    private const val MIN_BAND_HOURS = 3
    private const val LATEST_HOUR = HOURS_PER_DAY - MIN_BAND_HOURS

    /** The hours a user may choose for a habit. */
    val hourRange: IntRange = EARLIEST_HOUR..LATEST_HOUR

    fun clampHour(hour: Int): Int = hour.coerceIn(hourRange)

    /**
     * Whether the local hour [hourOfDay] is inside the band of a habit reminded at [reminderHour]:
     * from that hour to the end of the day. The hours before it are outside, which is what keeps a
     * nudge out of the small hours without a separate quiet-hours mechanism.
     *
     * One band running to midnight rather than a threshold asked at one moment, because the trigger
     * is inexact: a delivery that slips still lands inside it. Copy names the hour only as "around"
     * it for the same reason.
     */
    fun inBand(hourOfDay: Int, reminderHour: Int): Boolean = hourOfDay >= reminderHour

    /**
     * The next band opening strictly after [fromMs] — today's if it is still ahead, otherwise
     * tomorrow's.
     *
     * This is the fallback arm, used only when the state [NudgeEligibility.nextEligibleAt] needs
     * cannot be read: it knows nothing of what was sent, so it skips a band that is open but unsent.
     * Strictly after, so it can never re-arm the instant that just fired and spin. It reads the
     * instants off [bandOn], so the fallback and the rules cannot disagree about where a band opens.
     */
    fun nextBandAfter(fromMs: Long, zone: ZoneId, reminderHour: Int): Long {
        val today = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        return bandOn(today, zone, reminderHour).first.takeIf { it > fromMs }
            ?: bandOn(today.plusDays(1), zone, reminderHour).first
    }

    /**
     * The instants a habit's band covers on [date]: from its hour up to midnight. The same band
     * [inBand] reads by hour, as instants, for asking *when* it opens rather than *whether* it is
     * open now. It goes through the calendar (`atTime(...).atZone(zone)`) rather than adding hours to
     * an epoch, so a DST day still opens the band at its stated local hour.
     */
    fun bandOn(date: LocalDate, zone: ZoneId, reminderHour: Int): LongRange =
        date.atTime(reminderHour, 0).atZone(zone).toInstant().toEpochMilli() until
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
