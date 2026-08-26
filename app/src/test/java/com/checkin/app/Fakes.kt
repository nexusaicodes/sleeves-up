/*
 * The suite mocks nothing — every seam is stood up by hand, so JUnit and the coroutines test
 * dispatcher are the only test dependencies.
 *
 * This file holds the small fakes, one per seam, each a few lines of recording or canned answers:
 * FakeTimeSource (the clock, and the only place a midnight rollover is driven), FakeServiceController,
 * FakeNotifier, FakeCsvExporter, FakeEngagementInstallId, FakeEngagementReporter, FakeSessionAlarms.
 *
 * A fake gets its own file once it has behaviour worth reading on its own — an in-memory query
 * surface with ordering and filtering to honour. That is why FakeCheckInSessionDao and
 * FakeEngagementLog are separate: the second in particular mirrors the real log's source scoping,
 * because a fake that did not would let a test prove only the fake right.
 *
 * Every name here starts with Fake so `ls Fake*` and a grep for the seam's name both find it.
 */
package com.checkin.app

import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.engagement.EngagementInstallId
import com.checkin.app.notify.engagement.EngagementReporter
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.ExportResult
import com.checkin.app.platform.ServiceController
import com.checkin.app.service.SessionAlarms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneId

/** Deterministic clock. [day] is mutable so tests can drive a midnight rollover. */
class FakeTimeSource(private val now: Long, date: LocalDate, private val zone: ZoneId = ZoneId.of("UTC")) : TimeSource {
    val day = MutableStateFlow(date)
    override fun nowMillis(): Long = now
    override fun today(): LocalDate = day.value

    /** UTC by default so a test's start instants read back as the hour it wrote them at. */
    override fun zone(): ZoneId = zone
    override fun currentDay(): Flow<LocalDate> = day
}

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

    override fun revive(sessionId: Long, startedAt: Long): Boolean {
        if (!startAllowed) return false
        revived += sessionId
        return true
    }
    override fun stop() {
        stopCount++
    }
    override fun refreshFromDb() = Unit
}

/** Records what was posted, and can refuse like a revoked POST_NOTIFICATIONS does. */
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

class FakeCsvExporter(var result: ExportResult = ExportResult.Success) : CsvExporter {
    var lastRange: Pair<String, String>? = null
    override suspend fun export(
        startKey: String,
        endKey: String,
        summaries: Map<String, DailyAggregate>,
    ): ExportResult {
        lastRange = startKey to endKey
        return result
    }
}

class FakeEngagementInstallId(private val installId: String = "fake-install") : EngagementInstallId {
    override fun installId(): String = installId
}

class FakeEngagementReporter : EngagementReporter {
    val checkedInAt = mutableListOf<Long>()

    override suspend fun onNudgeOpened(atMillis: Long, key: String?, variant: Int) = Unit
    override suspend fun onCheckedIn(atMillis: Long) {
        checkedInAt += atMillis
    }
}

/** Records what was armed, so both alarms can be asserted without a platform AlarmManager. */
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
