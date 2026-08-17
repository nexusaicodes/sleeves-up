package com.checkin.app.notify.engagement

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
 * Pure, like [com.checkin.app.service.SessionSchedule]: `java.time` only, no clock, no `Context`, and
 * the zone threaded as an explicit parameter rather than read from the system inside.
 */
object NudgeSchedule {

    /**
     * The checkpoints, in the order they occur.
     *
     * Three rather than one because a single threshold is only ever asked at whatever moment the
     * trigger happens to fire; spreading them over the day means a delivery that slips still lands
     * inside a band. The hours are deliberately not quoted in any user-facing string — delivery is
     * best-effort, so naming a time promises a punctuality the app cannot keep.
     */
    enum class Checkpoint(val hour: Int) {
        MORNING(10),
        AFTERNOON(14),
        EVENING(19),
    }

    /**
     * Which checkpoint the local hour [hourOfDay] falls in, or null outside all of them.
     *
     * **Bands, not thresholds, and that is load-bearing.** [NudgeEligibility] takes the first
     * matching nudge in declaration order, so a `hour >= checkpoint` test would make every hour past
     * 10:00 match MORNING and the later two would be unreachable for good. Each band runs from its
     * own hour up to the next checkpoint, and the last runs to the end of the day; the hours before
     * the first return null, which is what keeps a nudge out of the small hours without a separate
     * quiet-hours mechanism.
     */
    fun checkpointAt(hourOfDay: Int): Checkpoint? = Checkpoint.entries.lastOrNull { hourOfDay >= it.hour }

    /**
     * The next checkpoint instant strictly after [fromMs], rolling to tomorrow's first once the day's
     * last one has passed.
     *
     * Strictly after, so re-arming from inside the receiver that just fired cannot schedule the same
     * instant again and spin. It goes through the calendar (`atTime(...).atZone(zone)`) rather than
     * adding hours to an epoch, so a DST day still puts each checkpoint at its stated local hour.
     */
    fun nextCheckpointAfter(fromMs: Long, zone: ZoneId): Long {
        val from = Instant.ofEpochMilli(fromMs).atZone(zone)
        val today = from.toLocalDate()
        return Checkpoint.entries
            .map { today.atTime(it.hour, 0).atZone(zone) }
            .firstOrNull { it.toInstant().toEpochMilli() > fromMs }
            ?.toInstant()?.toEpochMilli()
            ?: today.plusDays(1).atTime(Checkpoint.entries.first().hour, 0).atZone(zone).toInstant().toEpochMilli()
    }
}
