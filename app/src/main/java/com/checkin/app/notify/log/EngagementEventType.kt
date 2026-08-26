package com.checkin.app.notify.log

/**
 * What happened to a notification we sent.
 *
 * Its own file rather than a second declaration in [EngagementEvent]'s, for the reason
 * [EngagementSource] gives: this is the type most of `notify/` imports — the dismiss receiver, the
 * dispatcher, the reporter and the log itself — and the file named for the Room row is not where a
 * reader goes looking for it. [ServiceEventType], its counterpart for infrastructure rows, is
 * already split out this way.
 *
 * **Every name here is stored in `engagement.db`** as a string rather than an ordinal, so reordering
 * is safe and renaming is not.
 */
enum class EngagementEventType {
    /** Posted to the system tray. */
    SHOWN,

    /** The user tapped it. */
    OPENED,

    /** The user swiped it away. */
    DISMISSED,

    /** The user checked in soon enough after a SHOWN for it to plausibly be the cause. */
    CONVERTED,
}
