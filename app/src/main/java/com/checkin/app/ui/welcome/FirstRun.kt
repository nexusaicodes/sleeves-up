package com.checkin.app.ui.welcome

/**
 * Which one-time first-run step the app still owes the user, in the order it owes them.
 *
 * Pulled out of the composition for the reason every decision in this app is: a Composable cannot be
 * unit-tested on this suite, so a rule left inside one ships unpinned. Same shape and same reason as
 * `ui/presence/AuthGate`.
 *
 * **The `when` order is the rule.** The welcome is tested first, so on a fresh install the
 * introduction lands before the system permission dialog rather than behind it — today that dialog
 * is the very first thing a fresh install puts on screen, asking a user to accept notifications from
 * an app they have not seen a word about. That ordering is the whole reason the welcome exists, so
 * it lives here where a test can see it, rather than being left to the order two branches happen to
 * sit in.
 */
object FirstRun {

    /** The step still owed. Only [WELCOME] renders anything of its own. */
    enum class Step { WELCOME, ASK_NOTIFICATIONS, NONE }

    fun step(seenWelcome: Boolean, askedNotifications: Boolean): Step = when {
        // Deliberately ahead of the notifications test, so an install updated onto this build —
        // which already carries `notifications_asked` while `welcome_seen` defaults false — sees the
        // welcome once rather than never. Reading that flag as "this is an update, skip it" was the
        // alternative, and it couples the two: anything that ever came to set `notifications_asked`
        // earlier would silently disable the welcome for everyone, with nothing failing. Showing it
        // once to an existing install is free at ~0 installs; a feature that quietly never appears
        // is not.
        !seenWelcome -> Step.WELCOME
        !askedNotifications -> Step.ASK_NOTIFICATIONS
        else -> Step.NONE
    }
}
