package com.checkin.app.notify.nudge

import com.checkin.app.notify.nudge.NudgeVoice.Voice
import com.checkin.app.session.SessionSchedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.min
import kotlin.time.Instant

/** One habit as [NudgePlan] reads it. */
data class PlanHabit(
    val habitId: Long,
    /** The permanent identity, which seeds the wording's tie-break as on Android. */
    val uuid: String,
    /** The reminder's minute of the day, already clamped to `NudgeSchedule.minuteRange`. */
    val reminderMinute: Int,
    /** The last day before today holding a session of this habit, open ones included; null for none. */
    val lastSessionDayBeforeToday: LocalDate?,
    /** Whether today holds a session of this habit, an open one included. */
    val hasSessionToday: Boolean,
    /** Whether today's reminder has already been shown, so today's is spent. */
    val shownToday: Boolean,
    /** Every wording key shown for this habit within `NudgeVoice.HISTORY_MS`, and when last shown. */
    val shownWording: Map<String, Long>,
)

/** A voice's pools of wording keys, from which a title and a body are picked independently. */
data class VoiceKeys(val titles: List<NudgeVariant>, val bodies: List<NudgeVariant>)

/** One reminder to schedule: the habit, the instant, and the wording it carries. */
data class PlannedNudge(
    val habitId: Long,
    val date: LocalDate,
    val fireAtMs: Long,
    val voice: Voice,
    val titleKey: String,
    val bodyKey: String,
)

/**
 * Habit reminders planned ahead, for a platform that cannot decide when a notification fires: on
 * iOS a scheduled request fires on its own, with no code run, so each reminder is decided when it is
 * scheduled and the plan is redone whenever app code runs.
 *
 * The rules are Android's, asked of a plan instead of the moment: one reminder per habit per day,
 * none on a day already holding a session, in the habit's band from its minute up to midnight, a
 * reminder already due put off by `NudgeConfig.DUE_NOW_DELAY_MS`, and `NudgeConfig.MIN_GAP_MS` held
 * between any two habits' reminders, deferring the later one and dropping it if that runs past
 * midnight. Wording follows `NudgeVoice`: the voice from the last session day, the least recently
 * shown title and body, ties broken by the same seed.
 *
 * **Every future day is planned as if no session happens before it**, which is exactly the case in
 * which its reminder is still true: a session cancels the habit's reminder and replans, since every
 * check-in passes through the app. So a later day's voice turns returning as the empty days add up,
 * and each planned wording counts as shown when its reminder fires.
 */
object NudgePlan {

    /** Pending notifications iOS keeps per app, measured on a device (`docs/ios-decisions.md`). */
    const val PENDING_LIMIT = 64

    /** The furthest ahead a plan reaches, however few habits share the budget. */
    const val MAX_DAYS = 14

    /** Slots held back for the open session's reminders, which are scheduled at check-in. */
    const val RESERVED = SessionSchedule.MAX_REMINDERS_PER_SESSION

    /** Days a plan covers for [habitCount] habits within what the pending limit leaves them. */
    fun daysAhead(habitCount: Int): Int =
        if (habitCount <= 0) 0 else min(MAX_DAYS, (PENDING_LIMIT - RESERVED) / habitCount)

    /**
     * The reminders for [habits] from [nowMs] on, soonest first: at most [daysAhead] days of them, so
     * within the pending limit with [RESERVED] slots held back.
     *
     * @param lastShownAtMs the last reminder of any habit actually shown, from which the gap runs.
     * @param copy each voice's pools of wording keys.
     */
    fun plan(
        habits: List<PlanHabit>,
        nowMs: Long,
        zone: TimeZone,
        lastShownAtMs: Long?,
        copy: (Voice) -> VoiceKeys,
    ): List<PlannedNudge> {
        val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date
        val shown = habits.associate { it.habitId to it.shownWording.toMutableMap() }
        var lastFire = lastShownAtMs
        val planned = mutableListOf<PlannedNudge>()
        for (offset in 0 until daysAhead(habits.size)) {
            val day = today.plus(offset, DateTimeUnit.DAY)
            val candidates = habits
                .filter { offset > 0 || (!it.hasSessionToday && !it.shownToday) }
                .mapNotNull { habit ->
                    val band = NudgeSchedule.bandOn(day, zone, habit.reminderMinute)
                    val earliest = if (band.first > nowMs) band.first else nowMs + NudgeConfig.DUE_NOW_DELAY_MS
                    Triple(habit, band, earliest).takeIf { earliest in band }
                }
                .sortedBy { it.third }
            for ((habit, band, earliest) in candidates) {
                val fireAt = lastFire?.let { maxOf(earliest, it + NudgeConfig.MIN_GAP_MS) } ?: earliest
                if (fireAt !in band) continue
                lastFire = fireAt
                // A session today is the last one before any later day, since none is assumed after it.
                val lastSession = if (offset > 0 && habit.hasSessionToday) today else habit.lastSessionDayBeforeToday
                val voice = NudgeVoice.voiceFor(lastSession, day)
                val history = shown.getValue(habit.habitId)
                val (title, body) = word(habit.uuid, day, history, copy(voice))
                history[title] = fireAt
                history[body] = fireAt
                planned += PlannedNudge(habit.habitId, day, fireAt, voice, title, body)
            }
        }
        return planned
    }

    /** The least recently shown title and body in [pools], ties broken by the habit's seed for [day]. */
    private fun word(uuid: String, day: LocalDate, history: Map<String, Long>, pools: VoiceKeys): Pair<String, String> {
        val seed = NudgeVoice.seedFor(uuid, day)
        val title = NudgeVoice.pick(pools.titles, history, seed)
        val body = NudgeVoice.pick(pools.bodies, history, NudgeVoice.bodySeed(seed, pools.titles.size))
        return title.key to body.key
    }
}
