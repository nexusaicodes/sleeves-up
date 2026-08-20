package com.checkin.app

import com.checkin.app.ui.welcome.FirstRun
import com.checkin.app.ui.welcome.FirstRun.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order is the feature, and the defect it fixes is a system permission dialog arriving as the
 * first thing a fresh install puts on screen, asking for notifications from an app nothing has yet
 * described. A rule left as "the welcome branch happens to be written above the one holding the
 * permission composable" is a rule no test can see and any edit can undo, so it is stated here.
 */
class FirstRunTest {

    @Test
    fun `a fresh install is owed the welcome before anything else`() {
        assertEquals(Step.WELCOME, FirstRun.step(seenWelcome = false, askedNotifications = false))
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

    /**
     * The last page is what gives the permission dialog a reason, so a skip must not release it —
     * one tap on page 1 would otherwise reproduce the defect in full, dialog first and nothing said.
     */
    @Test
    fun `a skipped welcome does not release the notification request`() {
        assertFalse(FirstRun.asksNotificationsAfter(FirstRun.Exit.SKIPPED))
    }

    @Test
    fun `a welcome read through releases the notification request`() {
        assertTrue(FirstRun.asksNotificationsAfter(FirstRun.Exit.FINISHED))
    }
}
