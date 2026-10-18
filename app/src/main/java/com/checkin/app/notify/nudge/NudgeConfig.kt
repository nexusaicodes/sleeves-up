package com.checkin.app.notify.nudge

/**
 * How often a nudge may be sent. Its own file because this is what anyone tuning nudge cadence is
 * looking for, and the three files they would try first — [NudgeEligibility], which reads these
 * values but does not own them, [NudgeSchedule], which owns the band hours, and [NudgeCatalog],
 * which owns the copy — are all dead ends.
 *
 * Frequency is bounded two ways and the band hours are **not** one of them: this type holds the gap,
 * and [NudgeSnapshot.spentToday] is the other — one nudge per habit per day. **There is no daily
 * cap across habits**, deliberately: a user with four habits asked for four reminders, and a cap
 * would decide on their behalf which habits go unmentioned. The gap is what stops those reminders
 * arriving as a burst.
 *
 * **Cadence only, and there is no longer any other kind of nudge constant.** A reader may come here
 * hunting an attribution window — how long after a nudge a check-in still counted as caused by it.
 * There isn't one: conversion attribution was deleted along with the analytics that were its only
 * consumer, and [NudgeSendLog] now records a send and nothing else.
 */
data class NudgeConfig(
    /**
     * The minimum spacing between two nudges, **whichever habits they belong to**.
     *
     * Global rather than per habit, because per habit it would bound nothing: each habit sends once
     * a day anyway. What it stops is two habits with the same time — or one whose inexact alarm
     * slipped into another's — arriving back to back as a pair of high-importance messages, which
     * reads as the app malfunctioning rather than as two reminders.
     *
     * Note this defers a nudge rather than cancelling it: the nudge is not marked sent, and
     * [NudgeEligibility.nextAlarmAcross] arms the alarm for the instant the gap clears while the
     * band is still open. That is what keeps the rule from silently costing a habit its reminder,
     * and why [NudgeSchedule]'s latest choosable time leaves room for it before midnight.
     */
    val minGapMs: Long = DEFAULT_MIN_GAP_MS,
    /**
     * How long the alarm waits when the nudge it is armed for is already due — see
     * [NudgeEligibility.nextAlarmAt].
     *
     * Only an arm made off the back of something else lands on a due nudge: a process start, a new
     * reminder time, a pass that threw. The commonest of those is the user opening the app, and firing then
     * posts "you haven't checked in" seconds later over the screen they opened to check in from.
     * Half an hour gives them the chance to, and the pass that fires then asks again. It is also
     * what keeps a pass that throws every time from re-arming itself at the alarm's minimum
     * futurity, a wake every few seconds for the rest of the band.
     */
    val dueNowDelayMs: Long = DEFAULT_DUE_NOW_DELAY_MS,
) {
    companion object {
        private const val MINUTE_MS = 60L * 1_000L
        const val DEFAULT_MIN_GAP_MS = 90L * MINUTE_MS
        const val DEFAULT_DUE_NOW_DELAY_MS = 30L * MINUTE_MS
    }
}
