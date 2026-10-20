package com.checkin.app.notify

import android.content.Context
import com.checkin.app.util.withLatinDigits
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
     * [resId] with its format arguments filled in, in Latin digits whatever the language — the same
     * rule `MainActivity` applies to every string on screen, so a notification cannot number
     * differently from the app it came from. Not overridden by the Android resolver: filling the
     * template here rather than through `getString(resId, args)` is what applies the rule.
     */
    fun format(resId: Int, vararg args: Any): String =
        String.format(Locale.getDefault().withLatinDigits(), get(resId), *args)
}

class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int): String = context.getString(resId)
}
