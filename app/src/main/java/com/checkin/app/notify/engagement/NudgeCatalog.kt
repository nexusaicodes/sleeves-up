package com.checkin.app.notify.engagement

import com.checkin.app.R

/** One wording of a nudge. Held as resource ids so copy stays localizable. */
data class NudgeCopy(val titleRes: Int, val bodyRes: Int)

/**
 * The copy for each nudge, one entry per variant. Adding a variant here is all an experiment needs —
 * [com.checkin.app.notify.engagement.VariantAssigner] buckets against `variants(nudge).size`, and the
 * chosen index is written to the engagement log so conversion can be compared per variant.
 */
object NudgeCatalog {

    private val copy: Map<Nudge, List<NudgeCopy>> = mapOf(
        Nudge.NOT_CHECKED_IN_MORNING to listOf(
            NudgeCopy(R.string.nudge_morning_title_a, R.string.nudge_morning_body_a),
            NudgeCopy(R.string.nudge_morning_title_b, R.string.nudge_morning_body_b),
        ),
        Nudge.NOT_CHECKED_IN_AFTERNOON to listOf(
            NudgeCopy(R.string.nudge_afternoon_title_a, R.string.nudge_afternoon_body_a),
            NudgeCopy(R.string.nudge_afternoon_title_b, R.string.nudge_afternoon_body_b),
        ),
        Nudge.NOT_CHECKED_IN_EVENING to listOf(
            NudgeCopy(R.string.nudge_evening_title_a, R.string.nudge_evening_body_a),
            NudgeCopy(R.string.nudge_evening_title_b, R.string.nudge_evening_body_b),
        ),
    )

    /** Never empty — a nudge with no copy could be selected and then fail to render. */
    fun variants(nudge: Nudge): List<NudgeCopy> = copy[nudge] ?: error("No copy registered for $nudge")

    fun variant(nudge: Nudge, index: Int): NudgeCopy {
        val all = variants(nudge)
        return all[index.mod(all.size)]
    }
}
