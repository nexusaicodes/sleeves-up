package com.checkin.app.util

import android.content.Context
import android.provider.Settings

/**
 * Whether the system's animation scale is on — false when the user has chosen "remove animations".
 * The one reading of the setting, shared by `animationsEnabled` in Compose and by the home-screen
 * widget, which decides whether its names cycle when it is drawn, so the two cannot disagree.
 */
internal fun Context.animationScaleOn(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
