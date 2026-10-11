package com.checkin.app.notify.nudge

import com.checkin.app.notify.NotificationIds

/**
 * The kinds of nudge the app can send. Order is the evaluation priority should two
 * ever be eligible in one pass — the first match wins, so the most valuable nudge goes first; today
 * the checkpoint bands are disjoint, so at most one is. Names are persisted in the send ledger, so renaming a
 * constant orphans its history — and with it the per-checkpoint dedup for any send already recorded —
 * unless the old name is added to [LEGACY_KEYS].
 *
 * [notificationId] must reference a dedicated constant in [NotificationIds], never derive from this
 * enum's ordinal or position: two nudges sharing an id replace each other in the tray, and reordering
 * this enum must not change any existing id.
 */
enum class Nudge(val notificationId: Int, val checkpoint: NudgeSchedule.Checkpoint) {
    /**
     * The user hasn't checked in today. One per checkpoint rather than one with a threshold: each
     * carries its own copy, so the follow-up reads as a different message instead of the same string
     * arriving twice.
     *
     * Order matches [NudgeSchedule.Checkpoint], and the bands they map to do not overlap, so the
     * first-match rule in [NudgeEligibility] picks the checkpoint the current hour is actually in.
     */
    NOT_CHECKED_IN_FIRST(NotificationIds.NUDGE_NOT_CHECKED_IN_FIRST, NudgeSchedule.Checkpoint.FIRST),
    NOT_CHECKED_IN_FOLLOW_UP(NotificationIds.NUDGE_NOT_CHECKED_IN_FOLLOW_UP, NudgeSchedule.Checkpoint.FOLLOW_UP),
    ;

    companion object {
        /**
         * Ledger keys an earlier build recorded, mapped to the nudge that now owns their checkpoint.
         * Without these, a send recorded under an old name on the day of an update still counts
         * toward the daily cap but no longer marks its successor as sent, so the same reminder goes
         * out twice and the cap then refuses the follow-up. The evening key maps to nothing: it
         * counts toward the cap, and nothing now owns its band. Append, never replace.
         */
        private val LEGACY_KEYS: Map<String, Nudge> = mapOf(
            "NOT_CHECKED_IN_MORNING" to NOT_CHECKED_IN_FIRST,
            "NOT_CHECKED_IN_AFTERNOON" to NOT_CHECKED_IN_FOLLOW_UP,
        )

        /** The nudge a ledger [key] records, or null for one nothing now owns. */
        fun fromStoredKey(key: String): Nudge? = entries.firstOrNull { it.name == key } ?: LEGACY_KEYS[key]
    }
}
