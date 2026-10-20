package com.checkin.app.notify.nudge

/**
 * Everything [NudgeEligibility] is allowed to look at **for one habit**, gathered by the caller.
 * Passing a plain value object — rather than letting the decision reach into a repository or a
 * clock — is what keeps the rules pure, exhaustively testable, and unable to touch tracking logic.
 *
 * One snapshot per active habit; only [lastShownAtMs] is shared between them, because the gap is
 * the one rule that spans habits. The caller is [NudgeDispatcher], which does every read this holds
 * the results of; that is where to look for how a field is actually derived.
 */
data class NudgeSnapshot(
    val nowMillis: Long,
    /** Local minute of the day, 0-1439. */
    val minuteOfDay: Int,
    /**
     * The minute of the day chosen for this habit. Deliberately undefaulted: a builder that forgot it
     * would evaluate the band for the default time while the alarm was armed from the stored one — an
     * alarm firing at an instant the rules decline.
     */
    val reminderMinute: Int,
    /**
     * This habit has a session today, open or closed.
     *
     * This is the only presence question the rules ask, and it is deliberately about *today's record*
     * rather than about whether a session is open right now. A session still open from an earlier day
     * is not evidence the user is here: it is a session whose day-boundary close was lost to a force
     * stop or a package replace. Suppressing on "a row is open" would let such a session silence every
     * nudge indefinitely, on exactly the device that has stopped being opened — and the nudge it
     * silenced would be the one that surfaces the problem.
     *
     * Scoped to the habit, so checking in to one habit does not quieten another's reminder.
     */
    val hasCheckedInToday: Boolean,
    /**
     * This habit's nudges already attempted today, posted or refused, so one cannot be tried twice.
     *
     * The band is hours wide and [NudgeWorker] runs hourly, so without this a repeat pass inside it
     * re-selects the same nudge and reposts identical copy under the same id. A refused attempt
     * counts too, and that is what makes "a refused nudge waits for tomorrow" true across processes:
     * were only posts recorded, every re-arm would read a refused band as open and unsent, and a user
     * who had turned the channel off would be woken for a pass into it on every process start.
     */
    val spentToday: Set<Nudge> = emptySet(),
    /**
     * When the most recent nudge **of any habit** was shown today, or null if none has been.
     * Refusals excluded: the gap spaces what the user was shown.
     */
    val lastShownAtMs: Long? = null,
    val config: NudgeConfig = NudgeConfig(),
)
