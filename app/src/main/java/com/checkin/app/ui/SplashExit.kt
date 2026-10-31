package com.checkin.app.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.splashscreen.SplashScreen
import com.checkin.app.util.animationScaleOn

/** Lets the mark finish arriving, then shrinks and fades it into the app. */
internal fun SplashScreen.fadeIntoApp(context: Context) {
    setOnExitAnimationListener { provider ->
        // A splash drawn without its icon has no icon view, and androidx's getter throws on the
        // platform's null rather than returning it.
        val icon = runCatching { provider.iconView }.getOrNull()
        if (icon == null || !context.animationScaleOn()) {
            provider.remove()
            return@setOnExitAnimationListener
        }
        // Wall-clock based; throws when the icon never animated.
        val arrivalEnd = runCatching { provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis }
            .getOrDefault(0L)
        val wait = (arrivalEnd - System.currentTimeMillis()).coerceIn(0L, ARRIVAL_CAP_MS)
        var removed = false
        val remove = Runnable {
            if (!removed) {
                removed = true
                provider.remove()
            }
        }
        // The icon view, not the splash view: the mark is a SurfaceView a parent's alpha doesn't reach.
        icon.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setStartDelay(wait)
            .setDuration(FADE_MS)
            .withEndAction(remove)
            .start()
        // A cancelled animation skips its end action, which would leave the splash over the app.
        Handler(Looper.getMainLooper()).postDelayed(remove, wait + FADE_MS + REMOVE_GRACE_MS)
    }
}

/** `ic_splash_icon`'s duration. */
private const val ARRIVAL_CAP_MS = 680L

private const val FADE_MS = 220L

/** How long past the fade's own end the splash may stay before it is removed regardless. */
private const val REMOVE_GRACE_MS = 500L

private const val EXIT_SCALE = 0.92f
