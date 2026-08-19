package com.checkin.app.ui.welcome

import com.checkin.app.R

/** One page of the welcome tour: what it says, and nothing about how it looks. */
data class WelcomePage(val titleRes: Int, val bodyRes: Int)

/**
 * The tour, in order.
 *
 * Resource ids only, so the copy is a plain Kotlin value the unit suite can read — the pages
 * themselves are the one part of this screen a test can reach at all. The icons are chosen by the
 * composable rather than carried here: one is a drawable and two are Material vectors, and a model
 * holding both shapes would exist only to let this list name them.
 *
 * Three pages is the number the dot row draws and the number a reader will sit through. A fourth
 * belongs here only if it says something the other three do not.
 */
object WelcomePages {

    val all: List<WelcomePage> = listOf(
        // The app's thesis, and the only place it is ever stated to the user: a day counts because
        // it has a session. Everything downstream — no target, no verdict, no grade — follows.
        WelcomePage(R.string.welcome_showing_up_title, R.string.welcome_showing_up_body),
        // Said before the camera is ever reached, and it asks for nothing. The prominent disclosure
        // still runs in PresenceGate immediately before the permission request, where Play needs it.
        WelcomePage(R.string.welcome_presence_title, R.string.welcome_presence_body),
        // Last, because the notification permission dialog opens the moment this page is finished.
        WelcomePage(R.string.welcome_reminders_title, R.string.welcome_reminders_body),
    )
}
