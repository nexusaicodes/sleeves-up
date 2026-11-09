package com.checkin.app.testing

import com.checkin.app.service.ServiceController

class FakeServiceController : ServiceController {
    val started = mutableListOf<Long>()
    val startedAt = mutableListOf<Long>()
    var stopCount = 0

    /** Set to false to stand in for a platform that refused a background foreground-service start. */
    var startAllowed = true

    /** Revives are tracked apart from check-ins: the two actions must not be interchangeable. */
    val revived = mutableListOf<Long>()

    override fun startTimer(sessionId: Long, startedAt: Long): Boolean {
        if (!startAllowed) return false
        started += sessionId
        this.startedAt += startedAt
        return true
    }

    /**
     * [reviveAttempts] counts calls whether or not the start was allowed, because a refusal is an
     * ordinary outcome the watchdog absorbs — [revived] alone cannot tell a refused attempt from one
     * that was never made, which is exactly the distinction those tests are about.
     */
    var reviveAttempts = 0
        private set

    override fun revive(sessionId: Long, startedAt: Long): Boolean {
        reviveAttempts++
        if (!startAllowed) return false
        revived += sessionId
        return true
    }
    override fun stop() {
        stopCount++
    }
    override fun refreshFromDb() = Unit
}
