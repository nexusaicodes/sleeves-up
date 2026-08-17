package com.checkin.app.notify.engagement

/**
 * Decides which nudge — if any — to send for a given [NudgeSnapshot].
 *
 * This is the whole decision surface of the engagement system, and it is a pure function: no clock,
 * no database, no Android. Every experiment in timing, capping or targeting is a change here and
 * nowhere else, which is what keeps engagement work from reaching into tracking logic.
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
 * Gates run cheapest-and-broadest first: the global cap, then per-nudge ones.
 */
object NudgeEligibility {

    fun select(snapshot: NudgeSnapshot): Nudge? {
        if (snapshot.shownToday >= snapshot.config.maxPerDay) return null
        if (!spacedFarEnough(snapshot)) return null

        // Declaration order in Nudge is the priority order.
        return Nudge.entries.firstOrNull { nudge -> triggers(snapshot, nudge) }
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
     * Exhaustive over [Nudge], so a new one is a compile error here rather than a nudge that is
     * declared, given copy and an id, and then never selected by anything.
     *
     * Each checkpoint matches only the band it owns — see [NudgeSchedule.checkpointAt] for why that
     * must be a band and not a `>=` threshold — and only once a day, since a band is hours wide and
     * more than one thing asks the question inside it.
     */
    private fun triggers(snapshot: NudgeSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN_MORNING,
        Nudge.NOT_CHECKED_IN_AFTERNOON,
        Nudge.NOT_CHECKED_IN_EVENING,
        ->
            !snapshot.hasCheckedInToday &&
                nudge !in snapshot.alreadySentToday &&
                NudgeSchedule.checkpointAt(snapshot.hourOfDay) == nudge.checkpoint
    }
}
