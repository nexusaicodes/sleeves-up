/*
 * The suite mocks nothing — every seam is stood up by hand, so JUnit and the coroutines test
 * dispatcher are the only test dependencies.
 *
 * This file holds the small fakes, one per seam, each a few lines of recording or canned answers:
 * FakeTimeSource (the clock, and the only place a midnight rollover is driven), FakeServiceController,
 * FakeNotifier, FakeCsvExporter, FakePostedNudges, FakeNudgeSendLog, FakeSessionAlarms,
 * FakeReminderSettings, FakeNudgeAlarms, FakeHabitChannels.
 *
 * A fake gets its own file once it has behaviour worth reading on its own — an in-memory query
 * surface with ordering and filtering to honour. FakeCheckInSessionDao and FakeHabitDao earn it;
 * FakeNudgeSendLog does not, because the real ledger is a single unfiltered range query.
 *
 * Every name here starts with Fake so `ls Fake*` and a grep for the seam's name both find it.
 */
package com.checkin.app

import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.local.Habit
import com.checkin.app.notify.HabitChannels
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.NudgeAlarms
import com.checkin.app.notify.nudge.NudgeSchedule
import com.checkin.app.notify.nudge.NudgeSendLog
import com.checkin.app.notify.nudge.NudgeSending
import com.checkin.app.notify.nudge.PostedNudges
import com.checkin.app.notify.nudge.ReminderSettings
import com.checkin.app.notify.nudge.SharedPrefsReminderSettings
import com.checkin.app.platform.CsvExporter
import com.checkin.app.platform.ExportResult
import com.checkin.app.platform.ServiceController
import com.checkin.app.service.SessionAlarms
import com.checkin.app.service.SessionZones
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** Deterministic clock. [day] is mutable so tests can drive a midnight rollover. */
class FakeTimeSource(private val now: Long, date: LocalDate, private val zone: ZoneId = ZoneId.of("UTC")) : TimeSource {
    val day = MutableStateFlow(date)

    /** A date [today] reads ahead of [currentDay], as when the midnight emission stalls in deep sleep. */
    var todayAhead: LocalDate? = null
    override fun nowMillis(): Long = now
    override fun today(): LocalDate = todayAhead ?: day.value

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
    var lastHabitName: String? = null
    var lastSummaries: Map<String, DailyAggregate> = emptyMap()
    override suspend fun export(
        habitName: String?,
        startKey: String,
        endKey: String,
        summaries: Map<String, DailyAggregate>,
    ): ExportResult {
        lastRange = startKey to endKey
        lastHabitName = habitName
        lastSummaries = summaries
        return result
    }
}

class FakePostedNudges : PostedNudges {
    val retired = mutableListOf<Long>()
    val retireCount: Int get() = retired.size

    override fun retire(habitId: Long) {
        retired += habitId
    }
}

/**
 * In-memory send ledger. Small enough to live here because the real one has no scoping left to
 * mirror — one unfiltered range query is the whole read surface.
 */
class FakeNudgeSendLog : NudgeSendLog {
    val sends = mutableListOf<NudgeSending>()

    override suspend fun record(habitUuid: String, nudge: Nudge, atMillis: Long, posted: Boolean) {
        sends += NudgeSending(habitUuid, nudge.name, atMillis, posted)
    }

    override suspend fun attemptsSince(since: Long): List<NudgeSending> =
        sends.filter { it.atMillis >= since }.sortedBy { it.atMillis }

    override suspend fun prune(before: Long) {
        sends.removeAll { it.atMillis < before }
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

/** In-memory reminder minutes by habit uuid, clamping and defaulting exactly as the prefs-backed one does. */
class FakeReminderSettings(vararg minutes: Pair<String, Int>) : ReminderSettings {
    private val stored = MutableStateFlow(minutes.associate { (uuid, m) -> uuid to NudgeSchedule.clampMinute(m) })

    override fun reminderMinute(habitUuid: String): Int = stored.value[habitUuid] ?: NudgeSchedule.DEFAULT_MINUTE

    override fun setReminderMinute(habitUuid: String, minute: Int) {
        stored.value = stored.value + (habitUuid to NudgeSchedule.clampMinute(minute))
    }

    private var active: Set<String>? = null

    override fun earliestMinute(): Int = SharedPrefsReminderSettings.earliest(stored.value, active)

    override fun rememberActive(habitUuids: Set<String>) {
        active = habitUuids
    }

    override fun changes(): Flow<Unit> = stored.map { }
}

/** Records every reconcile, so a caller can be shown to have ensured a channel before posting to it. */
class FakeHabitChannels : HabitChannels {
    val synced = mutableListOf<List<Habit>>()

    override fun sync(habits: List<Habit>) {
        synced += habits
    }
}

/** Records each instant armed, so a caller can be shown to have armed the right one. */
class FakeNudgeAlarms(var throwOnArm: Boolean = false) : NudgeAlarms {
    val armed = mutableListOf<Long>()

    override fun arm(atMs: Long) {
        armed += atMs
        if (throwOnArm) error("alarm cap")
    }
}

/** Holds one recorded zone in memory, with the real seam's id check. */
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
