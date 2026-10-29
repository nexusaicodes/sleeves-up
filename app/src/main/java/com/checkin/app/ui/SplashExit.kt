package com.checkin.app.ui

import android.content.Context
import androidx.core.splashscreen.SplashScreen
import com.checkin.app.util.animationScaleOn

/**
 * How the splash leaves: once the app is ready, the mark finishes arriving, then shrinks slightly
 * and fades into the first frame, which has the splash's background colour, rather than cutting.
 *
 * The wait for the arrival is bounded by the icon's own animation ([ARRIVAL_CAP_MS] at most), so a
 * fast start costs well under a second and a slow one, which has already watched the mark land,
 * costs only the fade. With animations off the splash goes at once, as the platform would.
 */
internal fun SplashScreen.fadeIntoApp(context: Context) {
    setOnExitAnimationListener { provider ->
        if (!context.animationScaleOn()) {
            provider.remove()
            return@setOnExitAnimationListener
        }
        // The platform reports the arrival against the wall clock, and throws on a splash whose
        // icon never animated (a launch the system served without one), which leaves nothing to wait for.
        val arrivalEnd = runCatching { provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis }
            .getOrDefault(0L)
        val wait = (arrivalEnd - System.currentTimeMillis()).coerceIn(0L, ARRIVAL_CAP_MS)
        // The mark, not the splash view: the view's background is the first frame's own colour, so
        // fading it shows nothing, and the platform draws the mark on a SurfaceView, which a parent
        // view's alpha does not reach. Removing the splash once the mark is gone reveals the app.
        provider.iconView.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setStartDelay(wait)
            .setDuration(FADE_MS)
            .withEndAction { provider.remove() }
            .start()
    }
}

/** The splash icon's arrival, `ic_splash_icon`'s duration: no wait outlasts it. */
private const val ARRIVAL_CAP_MS = 680L

private const val FADE_MS = 220L

private const val EXIT_SCALE = 0.92f
