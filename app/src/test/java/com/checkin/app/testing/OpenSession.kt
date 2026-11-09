package com.checkin.app.testing

import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.Habit
import com.checkin.app.data.repository.CheckInRepository
import com.checkin.app.data.repository.CheckInResult

/** A check-in that must open a session; fails the test outright when it does not. */
internal suspend fun CheckInRepository.open(habitId: Long = Habit.SEED_ID): CheckInSession =
    (checkIn(habitId) as CheckInResult.Opened).session
