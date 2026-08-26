package com.checkin.app.notify

/**
 * Whether a post would actually be seen.
 *
 * Three separate switches can silence a notification and `notify` reports none of them — it returns
 * void and drops the post. The runtime permission is only one of them: a user can hold
 * `POST_NOTIFICATIONS` and still have notifications off for the whole app, or have this one channel
 * set to "None". Two callers act on the answer: the session reminder marks its alert spent on the
 * strength of it, so a wrong `true` leaves the first reminder the user can actually read arriving
 * silently mid-ladder, and the nudge dispatcher spends a slot in the daily cap on it.
 *
 * Pure, and separate from [AndroidNotifier], because that class is Android-only and so untestable on
 * this project's JVM-only suite — which would leave the decision every caller trusts as the one part
 * of the path nothing exercises.
 */
object NotificationDelivery {

    /** `NotificationManagerCompat.IMPORTANCE_NONE`, restated so this file needs no Android imports. */
    const val IMPORTANCE_NONE = 0

    /**
     * [channelImportance] is null when the channel does not exist, which counts as blocked: a post to
     * a channel that was never created is discarded. Channels are created at startup, so a null means
     * something is already wrong — and a notification nobody can see must not be reported as sent.
     */
    fun canDeliver(permissionGranted: Boolean, appEnabled: Boolean, channelImportance: Int?): Boolean =
        permissionGranted && appEnabled && channelImportance != null && channelImportance != IMPORTANCE_NONE
}
