package com.checkin.app.notify.nudge

import com.checkin.app.R

/** The wording of a nudge. Held as resource ids so copy stays localizable. */
data class NudgeCopy(val titleRes: Int, val bodyRes: Int)

/**
 * The copy for each nudge — exactly one wording apiece.
 *
 * There is deliberately **no variant mechanism**. Each nudge carried two arms bucketed per install,
 * and the only thing that could ever read which arm won was the conversion analytics this package no
 * longer keeps. An experiment whose result nothing can read is not an experiment, and re-adding one
 * means first building something that measures it.
 */
object NudgeCatalog {

    private val copy: Map<Nudge, NudgeCopy> = mapOf(
        Nudge.NOT_CHECKED_IN_MORNING to NudgeCopy(
            R.string.nudge_not_checked_in_morning_title,
            R.string.nudge_not_checked_in_morning_body,
        ),
        Nudge.NOT_CHECKED_IN_AFTERNOON to NudgeCopy(
            R.string.nudge_not_checked_in_afternoon_title,
            R.string.nudge_not_checked_in_afternoon_body,
        ),
        Nudge.NOT_CHECKED_IN_EVENING to NudgeCopy(
            R.string.nudge_not_checked_in_evening_title,
            R.string.nudge_not_checked_in_evening_body,
        ),
    )

    /** Errors rather than returning null — a nudge with no copy could be selected and then fail to render. */
    fun copyFor(nudge: Nudge): NudgeCopy = copy[nudge] ?: error("No copy registered for $nudge")
}
