package com.checkin.app.notify.nudge

import com.checkin.app.util.MILLIS_PER_HOUR
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A wording a nudge can take, identified by the stable [key] the send ledger records. */
interface NudgeVariant {
    /** Persisted in the ledger, so frozen once shipped: a renamed key loses its history. */
    val key: String
}

/**
 * Which tone a habit's reminder takes, and which wording within it — pure, so both are pinned by
 * `NudgeVoiceTest`.
 *
 * The voice is read from one fact, the habit's last session day before today, and picks a pool of
 * copy, nothing more. **The text never reveals it**: no line says how long it has been, names a
 * missed day, or reads as noticing a lapse, so any line from any pool reads naturally to anyone. A
 * lapse gets gentler, smaller asks, never louder ones — the user who has drifted is the one guilt
 * sends away. Nothing here sees hours or counts, and nothing it returns is displayed.
 */
object NudgeVoice {

    enum class Voice {
        /** The habit has never had a session. */
        NOT_STARTED,

        /** A session within the last [RETURNING_AFTER_DAYS] days. */
        STEADY,

        /** The last session was [RETURNING_AFTER_DAYS] or more days ago. */
        RETURNING,
    }

    /** One empty day is ordinary life; from three, the copy lowers the ask. */
    const val RETURNING_AFTER_DAYS = 3L

    /**
     * How far back [pick] is told what was shown, and so how long the ledger keeps a send. A nudge
     * posts only on a day without a session, at most once, so this spans several turns of the
     * largest pool even for a habit reminded every day.
     */
    const val HISTORY_MS = 60L * 24L * MILLIS_PER_HOUR

    /**
     * The voice for a habit whose last session before [today] was on [lastSessionDay]. A nudge is
     * only sent on a day with no session, so the gap is at least one day; a later date (a clock moved
     * back) reads as steady.
     */
    fun voiceFor(lastSessionDay: LocalDate?, today: LocalDate): Voice = when {
        lastSessionDay == null -> Voice.NOT_STARTED
        ChronoUnit.DAYS.between(lastSessionDay, today) < RETURNING_AFTER_DAYS -> Voice.STEADY
        else -> Voice.RETURNING
    }

    /**
     * The least recently shown variant of [pool]: one never shown first, otherwise the one shown
     * longest ago, per [lastShownAt] (key → when it was last shown). Repeats are therefore as far apart
     * as the pool allows, with no window constant to fall out of step with its size. Ties fall to
     * [seed], so habits sharing a day, or a fresh install, do not all open on the first variant.
     */
    fun <T : NudgeVariant> pick(pool: List<T>, lastShownAt: Map<String, Long>, seed: Long): T {
        require(pool.isNotEmpty()) { "Empty nudge pool" }
        val oldest = pool.minOf { lastShownAt[it.key] ?: Long.MIN_VALUE }
        val candidates = pool.filter { (lastShownAt[it.key] ?: Long.MIN_VALUE) == oldest }
        return candidates[Math.floorMod(seed, candidates.size.toLong()).toInt()]
    }

    /** The tie-break for [habitUuid]'s nudge on [today]: stable within the day, different across days. */
    fun seedFor(habitUuid: String, today: LocalDate): Long =
        habitUuid.hashCode().toLong() * SEED_PRIME + today.toEpochDay()

    private const val SEED_PRIME = 31L
}
