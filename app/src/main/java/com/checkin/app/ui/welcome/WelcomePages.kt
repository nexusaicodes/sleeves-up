package com.checkin.app.ui.welcome

import com.checkin.app.R

/**
 * Which mark a page carries. Named rather than held as an `ImageVector` so this file stays free of
 * Compose and the unit suite can read it — one is a drawable the app ships, one a Material vector and
 * one a carousel of them, which is a difference only the composable needs to know.
 */
enum class WelcomeIcon { CHECK_IN_MARK, FACE, WIDGET, HABITS }

/**
 * One page of the welcome tour: what it says and which mark it carries, not how it looks.
 * [addsWidget] gives the page an action that asks the launcher to place the home-screen widget.
 */
data class WelcomePage(val titleRes: Int, val bodyRes: Int, val icon: WelcomeIcon, val addsWidget: Boolean = false)

/**
 * The tour, in order — resource ids and a named icon, which is what makes it the one part of this
 * screen the unit suite can read.
 */
object WelcomePages {

    /** The widget's page, which also stands alone for installs that finished the tour without it. */
    val widget: WelcomePage get() = all.single { it.addsWidget }

    val all: List<WelcomePage> = listOf(
        // Why repetition, before anything about how.
        WelcomePage(
            titleRes = R.string.welcome_quote_title,
            bodyRes = R.string.welcome_quote_body,
            icon = WelcomeIcon.CHECK_IN_MARK,
        ),
        // The app's thesis, and the only place it is stated to the user. Informational about the
        // camera and asks for nothing: PresenceGate still raises the prominent disclosure
        // immediately before the permission request, where Play requires it.
        WelcomePage(
            titleRes = R.string.welcome_showing_up_title,
            bodyRes = R.string.welcome_showing_up_body,
            icon = WelcomeIcon.FACE,
        ),
        // The one way in that skips opening the app; the gate behind it is the one just described.
        // Also shown alone, once, to whoever finished the tour before this page existed.
        WelcomePage(
            titleRes = R.string.welcome_widget_title,
            bodyRes = R.string.welcome_widget_body,
            icon = WelcomeIcon.WIDGET,
            addsWidget = true,
        ),
        // Last, because it leads straight into the naming step, where the first habit is named and
        // its reminder time picked. The icon rides on the page rather than being picked by index, so
        // reordering these moves the mark with the words.
        WelcomePage(
            titleRes = R.string.welcome_habits_title,
            bodyRes = R.string.welcome_habits_body,
            icon = WelcomeIcon.HABITS,
        ),
    )
}
