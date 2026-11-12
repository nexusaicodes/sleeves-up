package com.checkin.app.testing

import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier

class FakeNotifier(var refuse: Boolean = false) : Notifier {
    val shown = mutableListOf<NotificationSpec>()
    val cancelled = mutableListOf<Int>()

    override fun show(spec: NotificationSpec): Boolean {
        if (refuse) return false
        shown += spec
        return true
    }

    override fun cancel(id: Int) {
        cancelled += id
    }
}
