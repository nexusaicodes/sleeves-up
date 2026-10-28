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
        assertEquals(
            Step.WELCOME,
            FirstRun.step(seenWelcome = false, habitNeedsName = true, askedNotifications = false),
        )
    }

    @Test
    fun `the welcome hands off to the notification request`() {
        assertEquals(
            Step.ASK_NOTIFICATIONS,
            FirstRun.step(seenWelcome = true, habitNeedsName = false, askedNotifications = false),
        )
    }

    @Test
    fun `nothing is owed once both have been done`() {
        assertEquals(Step.NONE, FirstRun.step(seenWelcome = true, habitNeedsName = false, askedNotifications = true))
    }

    /**
     * The naming step outranks the dialog, and the app, because the Check In button names the habit.
     * Its reachable case is an install updated onto habits: the tour long since seen, notifications
     * long since asked, and the record it carried still unnamed.
     */
    @Test
    fun `an unnamed habit is named before the notification request and the app`() {
        assertEquals(
            Step.NAME_HABIT,
            FirstRun.step(seenWelcome = true, habitNeedsName = true, askedNotifications = false),
        )
        assertEquals(
            Step.NAME_HABIT,
            FirstRun.step(seenWelcome = true, habitNeedsName = true, askedNotifications = true),
        )
    }

    /** The welcome still comes first: it is where a fresh install names its habit to begin with. */
    @Test
    fun `the welcome outranks naming`() {
        assertEquals(Step.WELCOME, FirstRun.step(seenWelcome = false, habitNeedsName = true, askedNotifications = true))
    }

    /** Never guessed before the habits table answers, in either direction. */
    @Test
    fun `an unanswered naming question holds everything after the welcome`() {
        assertEquals(Step.PENDING, FirstRun.step(seenWelcome = true, habitNeedsName = null, askedNotifications = false))
        assertEquals(Step.PENDING, FirstRun.step(seenWelcome = true, habitNeedsName = null, askedNotifications = true))
    }

    /**
     * The fourth cell, reachable only by updating onto this build: `notifications_asked` is already
     * true there while `welcome_seen` defaults false. Reading that as "an update, so skip it" would
     * couple the two flags, and anything that later set `notifications_asked` earlier would silently
     * disable the welcome for everyone.
     */
    @Test
    fun `an install updated onto this build still sees the welcome`() {
        assertEquals(
            Step.WELCOME,
            FirstRun.step(seenWelcome = false, habitNeedsName = false, askedNotifications = true),
        )
    }

    /** Walked rather than asserted cell by cell: what must hold is that it only ever moves forward. */
    @Test
    fun `the sequence never returns to the welcome`() {
        var seenWelcome = false
        var needsName = true
        var askedNotifications = false
        val walked = mutableListOf<Step>()

        repeat(4) {
            val step = FirstRun.step(seenWelcome, needsName, askedNotifications)
            walked += step
            when (step) {
                Step.WELCOME -> seenWelcome = true
                Step.NAME_HABIT -> needsName = false
                Step.ASK_NOTIFICATIONS -> askedNotifications = true
                Step.PENDING, Step.NONE -> Unit
            }
        }

        assertEquals(listOf(Step.WELCOME, Step.NAME_HABIT, Step.ASK_NOTIFICATIONS, Step.NONE), walked)
        assertEquals(Step.NONE, FirstRun.step(seenWelcome, needsName, askedNotifications))
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

    @Test
    fun `the widget page waits for the second day with a session`() {
        assertFalse(
            FirstRun.widgetPageOwed(
                seenWelcome = true,
                seenWidgetPage = false,
                daysWithSession = 0,
                widgetPlaced = false,
            ),
        )
        assertFalse(
            FirstRun.widgetPageOwed(
                seenWelcome = true,
                seenWidgetPage = false,
                daysWithSession = 1,
                widgetPlaced = false,
            ),
        )
        assertTrue(
            FirstRun.widgetPageOwed(
                seenWelcome = true,
                seenWidgetPage = false,
                daysWithSession = 2,
                widgetPlaced = false,
            ),
        )
        assertTrue(
            FirstRun.widgetPageOwed(
                seenWelcome = true,
                seenWidgetPage = false,
                daysWithSession = 40,
                widgetPlaced = false,
            ),
        )
    }

    /** The tour carries the page, so an install still owing the tour meets it there, not alone. */
    @Test
    fun `the widget page is never owed ahead of the tour`() {
        assertFalse(
            FirstRun.widgetPageOwed(
                seenWelcome = false,
                seenWidgetPage = false,
                daysWithSession = 5,
                widgetPlaced = false,
            ),
        )
    }

    @Test
    fun `the widget page is shown once, and not to whoever has a widget placed`() {
        assertFalse(
            FirstRun.widgetPageOwed(
                seenWelcome = true,
                seenWidgetPage = true,
                daysWithSession = 5,
                widgetPlaced = false,
            ),
        )
        assertFalse(
            FirstRun.widgetPageOwed(
                seenWelcome = true,
                seenWidgetPage = false,
                daysWithSession = 5,
                widgetPlaced = true,
            ),
        )
    }
}
