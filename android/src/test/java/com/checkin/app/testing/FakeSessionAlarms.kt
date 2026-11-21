package com.checkin.app.testing

import com.checkin.app.session.SessionAlarms

class FakeSessionAlarms(override var remindersSent: Int = 0) : SessionAlarms {
    val reminders = mutableListOf<Long>()
    val dayBoundaries = mutableListOf<Long>()
    var cancelCount = 0

    val lastReminder: Long? get() = reminders.lastOrNull()
    val lastDayBoundary: Long? get() = dayBoundaries.lastOrNull()

    override var nextReminderAt: Long = 0L
        private set

    override var dayBoundaryAt: Long = 0L
        private set

    override fun scheduleReminderAt(atMillis: Long) {
        reminders += atMillis
        nextReminderAt = atMillis
    }

    override fun scheduleDayBoundaryAt(atMillis: Long) {
        dayBoundaries += atMillis
        dayBoundaryAt = atMillis
    }

    /**
     * Mirrors the real seam: cancelling drops both alarms, both instants and the count together.
     * Kept in step deliberately — a fake that diverges here lets a test only ever prove the fake.
     */
    override fun cancelAll() {
        cancelCount++
        remindersSent = 0
        nextReminderAt = 0L
        dayBoundaryAt = 0L
    }

    /** Seeds what a previous process left armed, which is what `ensureArmed` reads. */
    fun seedArmed(reminderAt: Long, boundaryAt: Long) {
        nextReminderAt = reminderAt
        dayBoundaryAt = boundaryAt
    }
}
