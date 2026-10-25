package com.checkin.app.notify

/**
 * Whether a post would actually be seen.
 *
 * Four separate switches can silence a notification and `notify` reports none of them — it returns
 * void and drops the post. The runtime permission is only one of them: a user can hold
 * `POST_NOTIFICATIONS` and still have notifications off for the whole app, or have this one channel
 * set to "None", or the group a habit's channel sits in turned off. Two callers act on the answer:
 * the session reminder marks its alert spent on the strength of it, so a wrong `true` leaves the
 * first reminder the user can actually read arriving silently mid-ladder, and the nudge dispatcher
 * spaces every other habit's reminder off it.
 *
 * Pure, and separate from [AndroidNotifier], because that class is Android-only and so untestable on
 * this project's JVM-only suite — which would leave the decision every caller trusts as the one part
 * of the path nothing exercises.
 */
object NotificationDelivery {

    /** `NotificationManagerCompat.IMPORTANCE_NONE`, restated so this file needs no Android imports. */
    const val IMPORTANCE_NONE = 0

    /** `Build.VERSION_CODES.TIRAMISU`, the first release where `POST_NOTIFICATIONS` exists. */
    const val RUNTIME_GRANT_SDK = 33

    /**
     * Whether posting needs `POST_NOTIFICATIONS` granted at runtime on [sdkInt]. Below API 33 the
     * permission does not exist and notifications are on from install, yet `checkSelfPermission`
     * still reports it DENIED — read as a refusal there, it would refuse every post the app makes.
     */
    fun needsRuntimeGrant(sdkInt: Int): Boolean = sdkInt >= RUNTIME_GRANT_SDK

    /**
     * [channelImportance] is null when the channel does not exist, which counts as blocked: a post to
     * a channel that was never created is discarded. Channels are created before anything posts to
     * them, so a null means something is already wrong — and a notification nobody can see must not
     * be reported as sent.
     *
     * [groupBlocked] is the fourth switch, and the one only habit reminders have: system settings can
     * turn off a whole channel group — every habit's reminders at once — while each channel in it
     * still reports its own importance unchanged, and the platform drops the post.
     */
    fun canDeliver(
        permissionGranted: Boolean,
        appEnabled: Boolean,
        channelImportance: Int?,
        groupBlocked: Boolean,
    ): Boolean = permissionGranted &&
        appEnabled &&
        !groupBlocked &&
        channelImportance != null &&
        channelImportance != IMPORTANCE_NONE
}
