package com.checkin.app.notify.engagement

/**
 * Decides which nudge — if any — to send for a given [EngagementSnapshot].
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
 * **The daily cap is the whole frequency bound**; anything it allows is allowed. A per-nudge cooldown
 * beside it would measure a rolling window against the cap's calendar day — two rules disagreeing
 * about what a day is — while the checkpoints are already spaced hours apart by construction.
 *
 * There is likewise **no tracking-started gate**. These nudges — "you haven't checked in today" — are
 * for exactly the user who has not started yet, so requiring a first check-in would lock them away
 * from their audience. A user who finds them unwelcome turns the channel off, which is one long-press
 * on the notification itself.
 *
 * Gates run cheapest-and-broadest first: the global cap, then per-nudge ones.
 */
object NudgeEligibility {

    fun select(snapshot: EngagementSnapshot): Nudge? {
        if (snapshot.shownToday >= snapshot.config.maxPerDay) return null

        // Declaration order in Nudge is the priority order.
        return Nudge.entries.firstOrNull { nudge -> triggers(snapshot, nudge) }
    }

    /**
     * Exhaustive over [Nudge], so a new one is a compile error here rather than a nudge that is
     * declared, given copy and an id, and then never selected by anything.
     *
     * Each checkpoint matches only the band it owns — see [NudgeSchedule.checkpointAt] for why that
     * must be a band and not a `>=` threshold.
     */
    private fun triggers(snapshot: EngagementSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN_MORNING,
        Nudge.NOT_CHECKED_IN_AFTERNOON,
        Nudge.NOT_CHECKED_IN_EVENING,
        ->
            !snapshot.hasCheckedInToday &&
                !snapshot.isPresent &&
                NudgeSchedule.checkpointAt(snapshot.hourOfDay) == nudge.checkpoint
    }
}
