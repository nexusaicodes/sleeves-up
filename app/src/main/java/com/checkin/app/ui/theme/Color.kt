package com.checkin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// The one hue a recorded day is drawn in, tuned per theme so it keeps adequate contrast in light and
// dark. There is deliberately no second or third colour: days are never classified, so there is no
// "worse" shade for one to mean.
private val PresentLight = Color(0xFF2E7D32)
private val PresentDark = Color(0xFF81C784)

// Used only for the stop half of the start/stop control below. Not a status colour — no day is ever
// drawn in it.
private val StopLight = Color(0xFFC62828)
private val StopDark = Color(0xFFE57373)

// Label colors for the filled action buttons. Light theme fills with the deep hue and writes in
// white; dark theme fills with the pale hue and writes in near-black, which is how Material 3 keeps
// a colored container legible on a dark surface. Every pair clears WCAG AA (4.5:1) for body text.
private val OnStartDark = Color(0xFF0A2E12)
private val OnStopDark = Color(0xFF3B0A0A)

/**
 * Theme-aware colour for a day the user showed up.
 *
 * There is deliberately **one** strength and no overload taking a fraction. A day used to be drawn
 * at an alpha proportional to its hours against the user's longest day, which made the shade a
 * ranking: one nine-hour day permanently re-rendered every ordinary day as a fainter version of it.
 * A day either has a session or it does not, and that is the whole of what a cell says.
 *
 * Callers that need this over a background at reduced weight apply their own alpha for legibility —
 * a fixed constant, never a figure derived from a duration.
 */
@Composable
@ReadOnlyComposable
fun dayColor(): Color = if (isSystemInDarkTheme()) PresentDark else PresentLight

/** A filled button's container and the label on top of it. */
data class ActionColors(val container: Color, val content: Color)

/**
 * Colors for starting the clock.
 *
 * Deliberately the same green the calendar draws a recorded day in rather than a second, slightly
 * different one: the app should own one green and one red, not four near-misses.
 */
@Composable
@ReadOnlyComposable
fun startActionColors(): ActionColors = if (isSystemInDarkTheme()) {
    ActionColors(PresentDark, OnStartDark)
} else {
    ActionColors(PresentLight, Color.White)
}

/**
 * Colors for stopping the clock. Red here is the stop half of a start/stop control, not an error
 * state — nothing about checking out is a failure, and no copy on the screen says otherwise.
 */
@Composable
@ReadOnlyComposable
fun stopActionColors(): ActionColors = if (isSystemInDarkTheme()) {
    ActionColors(StopDark, OnStopDark)
} else {
    ActionColors(StopLight, Color.White)
}
