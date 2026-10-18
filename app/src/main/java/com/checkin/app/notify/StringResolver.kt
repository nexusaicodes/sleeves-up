package com.checkin.app.notify

import android.content.Context
import java.util.Locale

/**
 * Resolves a string resource id to text.
 *
 * Notification copy is held as resource ids so it stays localizable; this seam is what keeps
 * reading them from dragging a `Context` into the classes that decide what to post, so those stay
 * JVM-testable. Two consumers, in different packages:
 * [com.checkin.app.notify.nudge.NudgeDispatcher] and
 * [com.checkin.app.service.SessionLifecycleRunner]. It lives here rather than in `platform/` because
 * both of them resolve notification copy and nothing else does.
 */
fun interface StringResolver {
    fun get(resId: Int): String

    /**
     * [resId] with its format arguments filled in. Defaulted over [get] so a test's one-line fake
     * still formats; the Android one goes through the resources, as `getString` does.
     */
    fun format(resId: Int, vararg args: Any): String = String.format(Locale.getDefault(), get(resId), *args)
}

class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int): String = context.getString(resId)

    override fun format(resId: Int, vararg args: Any): String = context.getString(resId, *args)
}
