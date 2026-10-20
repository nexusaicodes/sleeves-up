package com.checkin.app.ui.welcome

/**
 * Which one-time first-run step the app still owes the user, in the order it owes them, and what
 * ending the welcome settles.
 *
 * **The `when` order is the rule**, and it lives here rather than in the composition because a
 * Composable cannot be unit-tested on this suite. The welcome comes first so the introduction lands
 * before the permission dialog rather than behind it — the ordering is the reason the welcome
 * exists, and left to which branch is written first it would be a rule no test can see. Naming a
 * habit comes between the two, so neither a fresh install (the tour names nothing) nor one updated
 * onto habits ever reaches the app with a habit it cannot name.
 */
object FirstRun {

    /**
     * The step still owed. [WELCOME] and [NAME_HABIT] render screens of their own; [PENDING] renders
     * a blank surface for the instant before the database has said whether a habit needs a name.
     */
    enum class Step { WELCOME, PENDING, NAME_HABIT, ASK_NOTIFICATIONS, NONE }

    /** How the welcome ended. */
    enum class Exit { FINISHED, SKIPPED }

    /**
     * [habitNeedsName] is null until the database has answered — it is a fact about the record, read
     * from the habits table rather than from `prompt_prefs`, so an install that passed the tour on
     * an earlier build is still asked exactly once, and a restore that brings the prefs back without
     * the record cannot mark the question answered.
     */
    fun step(seenWelcome: Boolean, habitNeedsName: Boolean?, askedNotifications: Boolean): Step = when {
        // Ahead of the notifications test, so an install updated from a build without the welcome —
        // already carrying `notifications_asked` while `welcome_seen` defaults false — sees the
        // welcome once rather than never. Reading that flag as "an update, skip it" instead couples
        // the two: anything that came to set it earlier would disable the welcome for everyone,
        // with nothing failing.
        !seenWelcome -> Step.WELCOME
        // Never guessed: drawing the app and then replacing it with the naming step would flash a
        // Check In button with no habit to name, and guessing "named" would let the gate open first.
        habitNeedsName == null -> Step.PENDING
        // Ahead of the dialog for the welcome's reason, and of the app because the Check In button
        // names the habit. The tour names nothing, so every fresh install comes here next.
        habitNeedsName -> Step.NAME_HABIT
        !askedNotifications -> Step.ASK_NOTIFICATIONS
        else -> Step.NONE
    }

    /**
     * Whether ending the welcome this way releases the launch-time notification request.
     *
     * Only a welcome read through does. The tour is what gives that dialog a reason, so a skip that
     * fired it anyway would raise it in one tap with nothing about the app said. A skip costs the
     * user no notifications: the presence gate still asks at the first check-in, beside the camera.
     */
    fun asksNotificationsAfter(exit: Exit): Boolean = exit == Exit.FINISHED
}
