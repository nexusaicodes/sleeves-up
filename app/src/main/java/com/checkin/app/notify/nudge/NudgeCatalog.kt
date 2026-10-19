package com.checkin.app.notify.nudge

import com.checkin.app.R

/**
 * The wording of a nudge. Held as resource ids so copy stays localizable. [titleRes] takes the
 * habit's name as its one argument, because a reminder that does not say which habit it is about is
 * one a user with two habits cannot act on; [unnamedTitleRes] stands in for a habit still owed a
 * name — an install updated onto habits whose user has not yet opened the app, which keeps being
 * reminded exactly as before the update.
 */
data class NudgeCopy(val titleRes: Int, val unnamedTitleRes: Int, val bodyRes: Int)

/**
 * The copy for each nudge — exactly one wording apiece.
 *
 * There is deliberately **no variant mechanism**: an experiment is only worth running with something
 * that reads which arm won, and nothing in the app measures that. Build the measurement first.
 */
object NudgeCatalog {

    private val copy: Map<Nudge, NudgeCopy> = mapOf(
        Nudge.NOT_CHECKED_IN to NudgeCopy(
            titleRes = R.string.nudge_not_checked_in_title,
            unnamedTitleRes = R.string.nudge_not_checked_in_unnamed_title,
            bodyRes = R.string.nudge_not_checked_in_body,
        ),
    )

    /** Errors rather than returning null — a nudge with no copy could be selected and then fail to render. */
    fun copyFor(nudge: Nudge): NudgeCopy = copy[nudge] ?: error("No copy registered for $nudge")
}
