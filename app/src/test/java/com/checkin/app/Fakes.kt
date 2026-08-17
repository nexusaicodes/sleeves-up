package com.checkin.app

import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.engagement.EngagementInstall
import com.checkin.app.notify.engagement.EngagementReporter
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeTrigger
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.ExportResult
import com.checkin.app.platform.ServiceController
import com.checkin.app.service.SessionAlarms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Deterministic clock. [day] is mutable so tests can drive a midnight rollover. */
class FixedTime(private val now: Long, date: LocalDate) : TimeSource {
    val day = MutableStateFlow(date)
    override fun nowMillis(): Long = now
    override fun today(): LocalDate = day.value
    override fun currentDay(): Flow<LocalDate> = day
}

class FakeServiceController : ServiceController {
    val started = mutableListOf<Long>()
    val startedAt = mutableListOf<Long>()
    var stopCount = 0
    var refreshCount = 0

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
    override fun refreshFromDb() {
        refreshCount++
    }
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

class FakeEngagementInstall(private val installId: String = "fake-install") : EngagementInstall {
    override fun installId(): String = installId
}

class FakeEngagementReporter : EngagementReporter {
    val openedAt = mutableListOf<Long>()
    val openedTags = mutableListOf<Pair<String?, Int>>()
    val checkedInAt = mutableListOf<Long>()

    override suspend fun onNudgeOpened(atMillis: Long, key: String?, variant: Int) {
        openedAt += atMillis
        openedTags += key to variant
    }
    override suspend fun onCheckedIn(atMillis: Long) {
        checkedInAt += atMillis
    }
}

class FakeNudgeTrigger : NudgeTrigger {
    var runOnceCount = 0
    val forced = mutableListOf<Pair<Nudge, Int?>>()
    var nextResult: Nudge? = null

    override suspend fun runOnce(): Nudge? {
        runOnceCount++
        return nextResult
    }

    override suspend fun forceSend(nudge: Nudge, variant: Int?): Nudge? {
        forced += nudge to variant
        return nudge
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
