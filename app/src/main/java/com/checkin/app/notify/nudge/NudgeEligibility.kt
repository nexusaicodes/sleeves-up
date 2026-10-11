package com.checkin.app.notify.nudge

import java.time.Instant
import java.time.ZoneId

/**
 * Decides which nudge — if any — to send for a given [NudgeSnapshot].
 *
 * This is the whole decision surface of the nudge layer, and it is a pure function: no clock, no
 * database, no Android. A change to timing, capping or targeting is a change here and nowhere else,
 * which is what keeps the nudge layer from reaching into tracking logic.
 *
 * **Nothing here asks whether the user wants nudges at all** — that is the notification channel's
 * question, and the channel is the only one who can answer it. A pref beside it would be subordinate
 * to it (a blocked channel refuses the post whatever the pref says) and so could only ever agree or
 * lie. Per-nudge control, if a second nudge ever needs it, is a second *channel*, never a flag here.
 *
 * There is likewise no do-not-disturb window: Android's per-channel settings already give the user
 * one, and an app-invented second policy would apply only to nudges while the session reminder and
 * the timer notification ignored it — a quiet window the app could not actually honour.
 *
 * **Frequency is bounded three ways, and each catches what the others cannot.** The daily cap limits
 * the day's total; per-checkpoint deduplication stops one band being sent twice however often the
 * question is asked; and a minimum gap keeps two *different* checkpoints apart when a deferred alarm
 * delivers them back to back. The checkpoint hours themselves guarantee none of this — an inexact
 * alarm is free to land hours late, which is the whole reason the last two rules exist.
 *
 * There is likewise **no tracking-started gate**. These nudges — "you haven't checked in today" — are
 * for exactly the user who has not started yet, so requiring a first check-in would lock them away
 * from their audience. A user who finds them unwelcome turns the channel off, which is one long-press
 * on the notification itself.
 *
 * Two questions are asked of the same rules: [select] — what to send now — and [nextEligibleAt] —
 * when it would next send something. Both go through [stillSendable] for everything that is not
 * a matter of time, so a gate added there binds both; `NudgeEligibilityTest` sweeps a day to pin
 * that the two agree on the time-dependent half too. [nextAlarmAt], what the alarm is armed for, is
 * [nextEligibleAt] with one difference, and only for a nudge already due.
 */
object NudgeEligibility {

    fun select(snapshot: NudgeSnapshot): Nudge? {
        if (!spacedFarEnough(snapshot)) return null

        // Declaration order in Nudge is the priority order.
        return Nudge.entries.firstOrNull { nudge ->
            stillSendable(snapshot, nudge) &&
                NudgeSchedule.checkpointAt(snapshot.hourOfDay, snapshot.firstReminderHour) == nudge.checkpoint
        }
    }

    /**
     * The earliest instant, at or after now, at which [select] would return a nudge if nothing else
     * changed in between — or tomorrow's first checkpoint when nothing is left to send today.
     *
     * For each checkpoint still sendable it is the later of its band opening and the minimum gap
     * clearing, or now when both have passed — kept only if that is still inside the band. That
     * covers the two cases a plain "next checkpoint" arm loses: a band that is open but unsent (a
     * re-arm after an update, a reboot or a new hour, landing after the follow-up hour has already
     * passed), which comes back as now; and a follow-up the gap is holding back, which comes back as
     * the instant the gap clears. A clock moved backwards puts the last send in the future, so the
     * gap clears later — exactly when [spacedFarEnough] would next allow it, never earlier.
     */
    fun nextEligibleAt(snapshot: NudgeSnapshot, zone: ZoneId): Long =
        earliest(snapshot, zone, dueAt = snapshot.nowMillis)

    /**
     * What the checkpoint alarm is armed for: [nextEligibleAt], except that a nudge already due is
     * put off by [NudgeConfig.dueNowDelayMs] — still inside its band, or the next candidate if the
     * delay runs past it. An instant still ahead is left exactly where it is, so an on-time
     * checkpoint and a gap-deferred follow-up both arrive when the rules allow them.
     */
    fun nextAlarmAt(snapshot: NudgeSnapshot, zone: ZoneId): Long =
        earliest(snapshot, zone, dueAt = snapshot.nowMillis + snapshot.config.dueNowDelayMs)

    /** The shared body of [nextEligibleAt] and [nextAlarmAt]: [dueAt] stands in for any instant already reached. */
    private fun earliest(snapshot: NudgeSnapshot, zone: ZoneId, dueAt: Long): Long {
        val today = Instant.ofEpochMilli(snapshot.nowMillis).atZone(zone).toLocalDate()
        val gapClearsAt = snapshot.lastShownAtMs?.let { it + snapshot.config.minGapMs } ?: Long.MIN_VALUE
        val todays = Nudge.entries
            .filter { stillSendable(snapshot, it) }
            .mapNotNull { nudge ->
                val band = NudgeSchedule.bandOn(nudge.checkpoint, today, zone, snapshot.firstReminderHour)
                val opensAt = maxOf(band.first, gapClearsAt)
                (if (opensAt > snapshot.nowMillis) opensAt else dueAt).takeIf { it in band }
            }
            .minOrNull()
        return todays ?: NudgeSchedule.bandOn(
            NudgeSchedule.Checkpoint.FIRST,
            today.plusDays(1),
            zone,
            snapshot.firstReminderHour,
        ).first
    }

    /**
     * Whether enough time has passed since the last nudge of the day.
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
     * Everything about whether [nudge] may still go out today that does not depend on the time:
     * the user has not checked in, the cap has room, and neither it nor a later checkpoint has been
     * attempted. Exhaustive over [Nudge], so a new one is a compile error here rather than a nudge that is
     * declared, given copy and an id, and then never selected by anything.
     *
     * **A later checkpoint's attempt spends the earlier ones too.** Dedup is per nudge and the bands
     * move with the chosen hour, so moving the hour later after the follow-up has gone would
     * otherwise reopen the first band and send "ready when you are" after "no session yet today" —
     * the two reminders in reverse order.
     */
    private fun stillSendable(snapshot: NudgeSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN_FIRST,
        Nudge.NOT_CHECKED_IN_FOLLOW_UP,
        ->
            !snapshot.hasCheckedInToday &&
                snapshot.shownToday < snapshot.config.maxPerDay &&
                snapshot.spentToday.none { it.checkpoint >= nudge.checkpoint }
    }
}
