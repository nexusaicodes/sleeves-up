package com.checkin.app.notify

/** A button on a notification. Tapping it opens the Activity with [launchExtra] set. */
data class NotificationAction(val iconRes: Int, val label: String, val launchExtra: String)

/** A notification to post. Presentation only — the decision to send lives in the caller's rules. */
data class NotificationSpec(
    val id: Int,
    val channelId: String,
    val title: String,
    val body: String,
    /** Extra flipped to true on the launch intent, so the Activity knows what the tap meant. */
    val launchExtra: String? = null,
    val actions: List<NotificationAction> = emptyList(),
    /**
     * A live status line rather than a message. From Android 14 the user can swipe one away even
     * though it is posted `ongoing` — on 13 it stays undismissible — and nothing re-posts it
     * afterwards, which would override a decision made with full information.
     */
    val ongoing: Boolean = false,
    val silent: Boolean = false,
    /**
     * Epoch-millis origin for a platform-rendered elapsed counter, or null for static [body] text.
     *
     * The system draws the ticking clock itself from a single post, and keeps counting through deep
     * sleep. Do not advance it by re-posting on a timer instead: that costs a main-thread binder call
     * per second (tens of thousands over a long session), gives each one a chance to throw, and still
     * freezes in deep sleep, because a coroutine `delay` is scheduled on uptime and uptime stops when
     * the CPU does. The origin is the DB row's `started_at`, the same instant the on-screen ticker
     * counts from, so the two agree rather than drifting by the check-in→service-start latency (see
     * [com.checkin.app.service.CheckInService.timerSpec]).
     */
    val chronometerBase: Long? = null,
)
