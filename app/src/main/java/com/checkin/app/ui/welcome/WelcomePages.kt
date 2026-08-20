package com.checkin.app.ui.welcome

import com.checkin.app.R

/**
 * Which mark a page carries. Named rather than held as an `ImageVector` so this file stays free of
 * Compose and the unit suite can read it — one of the three is a drawable the app ships and the
 * other two are Material vectors, which is a difference only the composable needs to know.
 */
enum class WelcomeIcon { CHECK_IN_MARK, FACE, REMINDER }

/** One page of the welcome tour: what it says and which mark it carries, not how it looks. */
data class WelcomePage(val titleRes: Int, val bodyRes: Int, val icon: WelcomeIcon)

/**
 * The tour, in order — resource ids and a named icon, which is what makes it the one part of this
 * screen the unit suite can read.
 */
object WelcomePages {

    val all: List<WelcomePage> = listOf(
        // The app's thesis, and the only place it is stated to the user.
        WelcomePage(
            titleRes = R.string.welcome_showing_up_title,
            bodyRes = R.string.welcome_showing_up_body,
            icon = WelcomeIcon.CHECK_IN_MARK,
        ),
        // Informational, and asks for nothing: PresenceGate still raises the prominent disclosure
        // immediately before the permission request, where Play requires it.
        WelcomePage(
            titleRes = R.string.welcome_presence_title,
            bodyRes = R.string.welcome_presence_body,
            icon = WelcomeIcon.FACE,
        ),
        // Last, because the notification permission dialog opens the moment this page is read
        // through. The icon rides on the page rather than being picked by index, so reordering these
        // moves the mark with the words.
        WelcomePage(
            titleRes = R.string.welcome_reminders_title,
            bodyRes = R.string.welcome_reminders_body,
            icon = WelcomeIcon.REMINDER,
        ),
    )
}
