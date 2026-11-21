package com.checkin.app.testing

import com.checkin.app.notify.nudge.Nudge
import com.checkin.app.notify.nudge.ledger.NudgeSendLog
import com.checkin.app.notify.nudge.ledger.NudgeSending

class FakeNudgeSendLog : NudgeSendLog {
    val sends = mutableListOf<NudgeSending>()

    override suspend fun record(
        habitUuid: String,
        nudge: Nudge,
        atMillis: Long,
        posted: Boolean,
        titleKey: String?,
        bodyKey: String?,
    ) {
        sends += NudgeSending(habitUuid, nudge.name, atMillis, posted, titleKey, bodyKey)
    }

    override suspend fun shownSince(habitUuid: String, since: Long): List<NudgeSending> =
        sends.filter { it.habitUuid == habitUuid && it.atMillis >= since && it.posted }

    override suspend fun attemptsSince(since: Long): List<NudgeSending> =
        sends.filter { it.atMillis >= since }.sortedBy { it.atMillis }

    override suspend fun prune(before: Long) {
        sends.removeAll { it.atMillis < before }
    }
}
