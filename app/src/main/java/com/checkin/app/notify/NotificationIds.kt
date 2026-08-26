package com.checkin.app.notify

/**
 * Every notification id the app posts under, including nudges: posting twice under one id replaces
 * rather than adds, so every sender's ids must be visible in one place.
 *
 * A [com.checkin.app.notify.engagement.Nudge] constant's id must be a dedicated constant here, never
 * derived from the enum's ordinal or position — reordering the enum must not change any existing id.
 *
 * ### PendingIntent request codes — the whole allocation
 *
 * A separate namespace from the ids above, shared process-wide **and with previously installed
 * versions**, whose notifications and alarms survive an update. [PendingIntent] equality ignores
 * extras, so two senders on one code means one silently rewrites the other's target. Four files
 * allocate out of it, so the bands are listed here — the ids file — rather than being discoverable
 * only by opening all four and hoping:
 *
 * | Band | Owner | Use |
 * |---|---|---|
 * | the id itself | [NotificationDismissReceiver] | that notification's delete intent |
 * | 1_000+ | [NotificationFactory.CONTENT_REQUEST_BASE] | a notification's tap target |
 * | 10_000+ | [NotificationFactory.ACTION_REQUEST_BASE] | its action buttons, [NotificationFactory.MAX_ACTIONS] apiece |
 * | 20_000, 20_001 | `service.SessionAlarms` | the session reminder and the day boundary |
 * | 20_010 | `notify.engagement.NudgeAlarms` | the nudge checkpoint |
 *
 * The first row sits below every band and overlaps their numbering deliberately: it is the only
 * `getBroadcast` allocation here, and a broadcast to this receiver can never be equal to an
 * activity intent whatever the code. Reuse that exemption only for another broadcast.
 *
 * **The values are frozen**, for the same reason the ids are. A fifth sender takes a fresh band
 * and adds a row here.
 */
object NotificationIds {

    /**
     * The ongoing check-in timer. A foreground-service notification, but swipeable at this minSdk
     * even though it is posted `ongoing` — see [NotificationSpec.ongoing]. Nothing re-posts it after
     * a dismissal; the session reminder and the day boundary are the coverage.
     */
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
