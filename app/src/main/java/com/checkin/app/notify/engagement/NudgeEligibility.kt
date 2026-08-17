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
 * beside it would, at `maxPerDay = 1`, only ever suppress a nudge the cap had already suppressed,
 * while measuring a rolling window against the cap's calendar day — two rules disagreeing about what
 * a day is.
 *
 * There is likewise **no tracking-started gate**. The one nudge that exists — "you haven't checked in
 * today" — is for exactly the user who has not started yet, so requiring a first check-in would lock
 * it away from its audience. A user who finds it unwelcome turns the channel off, which is one
 * long-press on the notification itself.
 *
 * Gates run cheapest-and-broadest first: the global cap, then per-nudge ones.
 */
object NudgeEligibility {

    fun select(snapshot: EngagementSnapshot): Nudge? {
        if (snapshot.shownToday >= snapshot.config.maxPerDay) return null

        // Declaration order in Nudge is the priority order.
        return Nudge.entries.firstOrNull { nudge -> triggers(snapshot, nudge) }
    }

    private fun triggers(snapshot: EngagementSnapshot, nudge: Nudge): Boolean = when (nudge) {
        Nudge.NOT_CHECKED_IN_BY ->
            !snapshot.hasCheckedInToday &&
                !snapshot.isCheckedIn &&
                snapshot.hourOfDay >= snapshot.config.notCheckedInByHour
    }
}
