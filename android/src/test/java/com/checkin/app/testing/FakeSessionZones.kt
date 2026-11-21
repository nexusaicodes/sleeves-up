package com.checkin.app.testing

import com.checkin.app.session.SessionZones
import java.time.ZoneId

class FakeSessionZones : SessionZones {
    var sessionId: Long? = null
        private set
    var zone: ZoneId? = null
        private set

    override fun record(sessionId: Long, zone: ZoneId) {
        this.sessionId = sessionId
        this.zone = zone
    }

    override fun zoneOf(sessionId: Long): ZoneId? = zone.takeIf { this.sessionId == sessionId }

    override fun clear() {
        sessionId = null
        zone = null
    }
}
