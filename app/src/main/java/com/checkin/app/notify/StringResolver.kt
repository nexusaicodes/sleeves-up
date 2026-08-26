package com.checkin.app.notify

import android.content.Context

/**
 * Resolves a string resource id to text.
 *
 * Notification copy is held as resource ids so it stays localizable; this seam is what keeps
 * reading them from dragging a `Context` into the classes that decide what to post, so those stay
 * JVM-testable. Two consumers, in different packages:
 * [com.checkin.app.notify.engagement.NudgeDispatcher] and
 * [com.checkin.app.service.SessionReminderRunner]. It lives here rather than in `platform/` because
 * both of them resolve notification copy and nothing else does.
 */
fun interface StringResolver {
    fun get(resId: Int): String
}

class AndroidStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int): String = context.getString(resId)
}
