package com.checkin.app

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.service.ServiceReconciler
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceReconcilerTest {

    @Test
    fun `no active session reconciles to Stop`() {
        assertEquals(ServiceReconciler.Result.Stop, ServiceReconciler.reconcile(null))
    }

    /** The row wins over whatever the advisory timer-prefs mirror had restored. */
    @Test
    fun `an active session is adopted with its DB values`() {
        val active = CheckInSession(id = 7, startedAt = 1000L, dateKey = "2026-06-15", habitId = 3L)

        val result = ServiceReconciler.reconcile(active)

        assertEquals(ServiceReconciler.Result.Adopt(sessionId = 7, startTime = 1000L, habitId = 3L), result)
    }
}
