package com.checkin.app.notify.nudge

import java.time.Instant
import java.time.ZoneId

/**
 * Decides which nudge — if any — to send for one habit's [NudgeSnapshot], and when the single
 * checkpoint alarm should next fire across all of them.
 *
 * This is the whole decision surface of the nudge layer, and it is a pure function: no clock, no
 * database, no Android. A change to timing, spacing or targeting is a change here and nowhere else,
 * which is what keeps the nudge layer from reaching into tracking logic.
 *
 * **Nothing here asks whether the user wants nudges at all** — that is the notification channel's
 * question, and each habit has its own channel. A pref beside it would be subordinate to it (a
 * blocked channel refuses the post whatever the pref says) and so could only ever agree or lie.
 *
 * There is likewise no do-not-disturb window: Android's per-channel settings already give the user
 * one, and an app-invented second policy would apply only to nudges while the session reminder and
 * the timer notification ignored it — a quiet window the app could not actually honour.
 *
 * **Frequency is bounded two ways, and each catches what the other cannot.** One nudge per habit per
 * day stops a band being sent twice however often the question is asked; a minimum gap across habits
 * keeps two reminders apart when their bands open together or a deferred alarm delivers them back
 * to back. The band hours themselves guarantee neither — an inexact alarm is free to land hours late.
 *
 * There is likewise **no tracking-started gate**. These nudges — "you haven't checked in today" — are
 * for exactly the user who has not started yet, so requiring a first check-in would lock them away
 * from their audience. A user who finds them unwelcome turns the habit's channel off, which is one
 * long-press on the notification itself.
 *
 * Two questions are asked of the same rules: [select] — what to send now — and [nextEligibleAt] —
 * when it would next send something. Both go through [stillSendable] for everything that is not
 * a matter of time, so a gate added there binds both; `NudgeEligibilityTest` sweeps a day to pin
 * that the two agree on the time-dependent half too. [nextAlarmAt], what the alarm is armed for, is
 * [nextEligibleAt] with one difference, and only for a nudge already due; [nextAlarmAcross] is its
 * minimum over every habit, because the habits share one alarm.
 */
object NudgeEligibility {

    fun select(snapshot: NudgeSnapshot): Nudge? {
        if (!spacedFarEnough(snapshot)) return null
        return Nudge.entries.firstOrNull { nudge ->
            stillSendable(snapshot, nudge) && NudgeSchedule.inBand(snapshot.minuteOfDay, snapshot.reminderMinute)
        }
    }

    /**
     * The earliest instant, at or after now, at which [select] would return a nudge for this habit if
     * nothing else changed in between — or tomorrow's band opening when nothing is left to send today.
     *
     * It is the later of the band opening and the minimum gap clearing, or now when both have passed
     * — kept only if that is still inside the band. That covers the two cases a plain "next band"
     * arm loses: a band that is open but unsent (a re-arm after an update, a reboot or a new time),
     * which comes back as now; and a reminder the gap is holding back behind another habit's, which
     * comes back as the instant the gap clears. A clock moved backwards puts the last send in the
     * future, so the gap clears later — exactly when [spacedFarEnough] would next allow it.
     */
    fun nextEligibleAt(snapshot: NudgeSnapshot, zone: ZoneId): Long =
        earliest(snapshot, zone, dueAt = snapshot.nowMillis)

    /**
     * What the checkpoint alarm is armed for on this habit's account: [nextEligibleAt], except that a
     * nudge already due is put off by [NudgeConfig.dueNowDelayMs] — still inside its band, or
     * tomorrow's if the delay runs past midnight. An instant still ahead is left exactly where it is,
     * so an on-time reminder and a gap-deferred one both arrive when the rules allow them.
     */
    fun nextAlarmAt(snapshot: NudgeSnapshot, zone: ZoneId): Long =
        earliest(snapshot, zone, dueAt = snapshot.nowMillis + snapshot.config.dueNowDelayMs)

    /**
     * The instant the one checkpoint alarm is armed for: the soonest [nextAlarmAt] of any habit, or
     * null when there are no habits to remind. One alarm rather than one per habit, because a
     * `PendingIntent` per habit is a request code per habit, and the pass the alarm runs already
     * asks every habit at once.
     */
    fun nextAlarmAcross(snapshots: List<NudgeSnapshot>, zone: ZoneId): Long? =
        snapshots.minOfOrNull { nextAlarmAt(it, zone) }

    /** The shared body of [nextEligibleAt] and [nextAlarmAt]: [dueAt] stands in for any instant already reached. */
    private fun earliest(snapshot: NudgeSnapshot, zone: ZoneId, dueAt: Long): Long {
        val today = Instant.ofEpochMilli(snapshot.nowMillis).atZone(zone).toLocalDate()
        val gapClearsAt = snapshot.lastShownAtMs?.let { it + snapshot.config.minGapMs } ?: Long.MIN_VALUE
        val todays = Nudge.entries
            .filter { stillSendable(snapshot, it) }
            .mapNotNull {
                val band = NudgeSchedule.bandOn(today, zone, snapshot.reminderMinute)
                val opensAt = maxOf(band.first, gapClearsAt)
                (if (opensAt > snapshot.nowMillis) opensAt else dueAt).takeIf { it in band }
            }
            .minOrNull()
        return todays ?: NudgeSchedule.bandOn(today.plusDays(1), zone, snapshot.reminderMinute).first
    }

    /**
     * Whether enough time has passed since the last nudge of the day, of any habit.
     *
     * A clock moved backwards makes the difference negative, which reads as "not far enough" and
     * suppresses. That is the direction to fail in: a missed nudge costs one message, while a rule
     * that unlocks on a clock change hands the user a burst of them.
     */
    private fun spacedFarEnough(snapshot: NudgeSnapshot): Boolean {
        val last = snapshot.lastShownAtMs ?: return true
        return snapshot.nowMillis - last >= snapshot.config.minGapMs
    }

    /**
     * Everything about whether [nudge] may still go out today for this habit that does not depend on
     * the time: the habit has no session today, and the nudge has not been attempted. Exhaustive over
     * [Nudge], so a new one is a compile error here rather than a nudge that is declared, given copy
     * and a slot, and then never selected by anything.
     */
    private fun stillSendable(snapshot: NudgeSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN -> !snapshot.hasCheckedInToday && nudge !in snapshot.spentToday
    }
}
