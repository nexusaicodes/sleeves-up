package com.checkin.app.notify.log

/**
 * The credit decision, kept pure and separate from storage so the Room implementation and any test
 * double share one definition of "did this nudge cause this check-in" rather than each restating it.
 *
 * The window itself is a parameter rather than a constant here, and it has exactly one supplier in
 * the app: `DefaultEngagementReporter.CONVERSION_WINDOW_MS`. Tuning it means editing that, not this.
 */
object AttributionRules {

    /**
     * A nudge shown at [shownAt] earns credit for an action at [actionAt] when the action came after
     * it, inside [windowMs], nothing has already been credited since that showing, and the user has
     * not swiped a nudge away since it was shown.
     *
     * [latestConvertedAt] is what keeps a conversion rate at or below 100%: without it, every
     * subsequent check-in in the window would be credited to the same notification.
     *
     * [latestDismissedAt] is what keeps it from crediting a rejection. A nudge the user swiped away
     * did not cause the check-in that happened to follow it, and counting it would make the one
     * signal the dismiss receiver exists to capture invisible to the only metric that reads it.
     */
    fun canCredit(
        shownAt: Long,
        actionAt: Long,
        windowMs: Long,
        latestConvertedAt: Long?,
        latestDismissedAt: Long?,
    ): Boolean = actionAt >= shownAt &&
        actionAt - shownAt <= windowMs &&
        (latestConvertedAt == null || latestConvertedAt < shownAt) &&
        (latestDismissedAt == null || latestDismissedAt < shownAt)
}
