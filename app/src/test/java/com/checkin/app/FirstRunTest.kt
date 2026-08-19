package com.checkin.app

import com.checkin.app.ui.welcome.FirstRun
import com.checkin.app.ui.welcome.FirstRun.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The order is the feature. A rule left as "the welcome branch happens to be written above the one
 * holding the permission composable" is a rule no test can see and any edit can undo, so it is
 * stated here instead.
 */
class FirstRunTest {

    @Test
    fun `a fresh install is owed the welcome before anything else`() {
        assertEquals(Step.WELCOME, FirstRun.step(seenWelcome = false, askedNotifications = false))
    }

    /**
     * The defect this whole screen exists to fix: the system permission dialog used to be the first
     * thing a fresh install put on screen, asking for notifications from an app nothing had yet
     * described.
     */
    @Test
    fun `the notification request is never the first thing owed`() {
        assertNotEquals(Step.ASK_NOTIFICATIONS, FirstRun.step(seenWelcome = false, askedNotifications = false))
    }

    @Test
    fun `the welcome hands off to the notification request`() {
        assertEquals(Step.ASK_NOTIFICATIONS, FirstRun.step(seenWelcome = true, askedNotifications = false))
    }

    @Test
    fun `nothing is owed once both have been done`() {
        assertEquals(Step.NONE, FirstRun.step(seenWelcome = true, askedNotifications = true))
    }

    /**
     * The fourth cell, reachable only by updating onto this build: `notifications_asked` is already
     * true there while `welcome_seen` defaults false. Reading that as "an update, so skip it" would
     * couple the two flags, and anything that later set `notifications_asked` earlier would silently
     * disable the welcome for everyone.
     */
    @Test
    fun `an install updated onto this build still sees the welcome`() {
        assertEquals(Step.WELCOME, FirstRun.step(seenWelcome = false, askedNotifications = true))
    }

    /** Walked rather than asserted cell by cell: what must hold is that it only ever moves forward. */
    @Test
    fun `the sequence never returns to the welcome`() {
        var seenWelcome = false
        var askedNotifications = false
        val walked = mutableListOf<Step>()

        repeat(3) {
            val step = FirstRun.step(seenWelcome, askedNotifications)
            walked += step
            when (step) {
                Step.WELCOME -> seenWelcome = true
                Step.ASK_NOTIFICATIONS -> askedNotifications = true
                Step.NONE -> Unit
            }
        }

        assertEquals(listOf(Step.WELCOME, Step.ASK_NOTIFICATIONS, Step.NONE), walked)
        assertEquals(Step.NONE, FirstRun.step(seenWelcome, askedNotifications))
    }
}
