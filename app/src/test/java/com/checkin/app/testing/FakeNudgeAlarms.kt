package com.checkin.app.testing

import com.checkin.app.notify.nudge.NudgeAlarms

class FakeNudgeAlarms(var throwOnArm: Boolean = false) : NudgeAlarms {
    val armed = mutableListOf<Long>()

    override fun arm(atMs: Long) {
        armed += atMs
        if (throwOnArm) error("alarm cap")
    }
}
