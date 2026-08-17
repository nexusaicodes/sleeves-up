package com.checkin.app.notify

/**
 * Every notification id the app posts under, including nudges: posting twice under one id replaces
 * rather than adds, so every sender's ids must be visible in one place.
 *
 * A [com.checkin.app.notify.engagement.Nudge] constant's id must be a dedicated constant here, never
 * derived from the enum's ordinal or position — reordering the enum must not change any existing id.
 */
object NotificationIds {

    /** The ongoing check-in timer. Foreground-service notification, never dismissible. */
    const val TIMER = 1

    /** The periodic "still going?" reminder for an open session; each one replaces the last. */
    const val SESSION_REMINDER = 2

    /**
     * One per checkpoint nudge. Distinct ids rather than a shared one, so the tray never silently
     * swaps one message for another behind the app's back — when a later checkpoint *should* replace
     * an earlier one, [com.checkin.app.notify.engagement.NudgeDispatcher] cancels its siblings
     * explicitly, which is a decision in code rather than a side effect of an id collision.
     */
    const val NUDGE_NOT_CHECKED_IN_MORNING = 10
    const val NUDGE_NOT_CHECKED_IN_AFTERNOON = 11
    const val NUDGE_NOT_CHECKED_IN_EVENING = 12
}
