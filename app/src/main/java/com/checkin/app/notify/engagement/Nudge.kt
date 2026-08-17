package com.checkin.app.notify.engagement

import com.checkin.app.notify.NotificationIds

/**
 * The kinds of engagement notification the app can send. Order is the evaluation priority when more
 * than one is eligible in the same pass — the first match wins, so the most valuable nudge goes
 * first. Names are persisted in the engagement log, so renaming a constant orphans its history.
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
