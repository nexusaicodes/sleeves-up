package com.checkin.app.testing

import com.checkin.app.notify.nudge.PostedNudges

class FakePostedNudges : PostedNudges {
    val retired = mutableListOf<Long>()
    val retireCount: Int get() = retired.size

    override fun retire(habitId: Long) {
        retired += habitId
    }
}
