package com.checkin.app.ui

import android.content.Context
import androidx.core.splashscreen.SplashScreen
import com.checkin.app.util.animationScaleOn

/** Lets the mark finish arriving, then shrinks and fades it into the app. */
internal fun SplashScreen.fadeIntoApp(context: Context) {
    setOnExitAnimationListener { provider ->
        if (!context.animationScaleOn()) {
            provider.remove()
            return@setOnExitAnimationListener
        }
        // Wall-clock based; throws when the icon never animated.
        val arrivalEnd = runCatching { provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis }
            .getOrDefault(0L)
        val wait = (arrivalEnd - System.currentTimeMillis()).coerceIn(0L, ARRIVAL_CAP_MS)
        // The icon view, not the splash view: the mark is a SurfaceView a parent's alpha doesn't reach.
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

/** `ic_splash_icon`'s duration. */
private const val ARRIVAL_CAP_MS = 680L

private const val FADE_MS = 220L

private const val EXIT_SCALE = 0.92f
