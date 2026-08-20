package com.checkin.app.ui.welcome

import com.checkin.app.R

/** One page of the welcome tour: what it says, and nothing about how it looks. */
data class WelcomePage(val titleRes: Int, val bodyRes: Int)

/**
 * The tour, in order — resource ids only, which is what makes it the one part of this screen the
 * unit suite can read. Icons are chosen by the composable, since one is a drawable and two are
 * Material vectors.
 */
object WelcomePages {

    val all: List<WelcomePage> = listOf(
        // The app's thesis, and the only place it is stated to the user.
        WelcomePage(R.string.welcome_showing_up_title, R.string.welcome_showing_up_body),
        // Informational, and asks for nothing: PresenceGate still raises the prominent disclosure
        // immediately before the permission request, where Play requires it.
        WelcomePage(R.string.welcome_presence_title, R.string.welcome_presence_body),
        // Last, because the notification permission dialog opens the moment this page is finished.
        WelcomePage(R.string.welcome_reminders_title, R.string.welcome_reminders_body),
    )
}
