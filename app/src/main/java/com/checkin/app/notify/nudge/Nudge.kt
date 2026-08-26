package com.checkin.app.notify.nudge

import com.checkin.app.notify.NotificationIds

/**
 * The kinds of nudge the app can send. Order is the evaluation priority should two
 * ever be eligible in one pass — the first match wins, so the most valuable nudge goes first; today
 * the checkpoint bands are disjoint, so at most one is. Names are persisted in the send ledger, so renaming a
 * constant orphans its history — and with it the per-checkpoint dedup for any send already recorded.
 *
 * [notificationId] must reference a dedicated constant in [NotificationIds], never derive from this
 * enum's ordinal or position: two nudges sharing an id replace each other in the tray, and reordering
 * this enum must not change any existing id.
 */
enum class Nudge(val notificationId: Int, val checkpoint: NudgeSchedule.Checkpoint) {
    /**
     * The user hasn't checked in today. One per checkpoint rather than one with a threshold: each
     * carries its own copy, so a second send in a day reads as a different message instead of the
     * same string arriving twice.
     *
     * Order matches [NudgeSchedule.Checkpoint], and the bands they map to do not overlap, so the
     * first-match rule in [NudgeEligibility] picks the checkpoint the current hour is actually in.
     */
    NOT_CHECKED_IN_MORNING(NotificationIds.NUDGE_NOT_CHECKED_IN_MORNING, NudgeSchedule.Checkpoint.MORNING),
    NOT_CHECKED_IN_AFTERNOON(NotificationIds.NUDGE_NOT_CHECKED_IN_AFTERNOON, NudgeSchedule.Checkpoint.AFTERNOON),
    NOT_CHECKED_IN_EVENING(NotificationIds.NUDGE_NOT_CHECKED_IN_EVENING, NudgeSchedule.Checkpoint.EVENING),
}
