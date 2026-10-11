package com.checkin.app

import com.checkin.app.ui.welcome.WelcomePages
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pages are the one part of this screen a JVM suite can reach — the pager, the dots and the
 * buttons are all Composables, and nothing here can compose one.
 */
class WelcomePagesTest {

    /** The count the dot row draws and the index the last page is found at. */
    @Test
    fun `the tour is three pages`() {
        assertEquals(3, WelcomePages.all.size)
    }

    /**
     * A page duplicated while adding another reads as a pager that has stopped moving: the dots
     * advance, the words don't.
     */
    @Test
    fun `no two pages carry the same copy`() {
        val titles = WelcomePages.all.map { it.titleRes }
        val bodies = WelcomePages.all.map { it.bodyRes }

        assertEquals(titles.size, titles.toSet().size)
        assertEquals(bodies.size, bodies.toSet().size)
    }

    /**
     * The icon rides on the page so that reordering the tour moves the mark with the words. Picked
     * by pager index instead, a reorder left the thesis page under a notifications bell and nothing
     * failed — the coupling was real and beyond the reach of any test.
     */
    @Test
    fun `no two pages carry the same icon`() {
        val icons = WelcomePages.all.map { it.icon }

        assertEquals(icons.size, icons.toSet().size)
    }

    /**
     * The hour is chosen on the reminders page and nowhere else, and that page is last: the
     * notification dialog follows it, so the reminders are described, timed and then requested.
     */
    @Test
    fun `only the last page chooses the reminder hour`() {
        val choosing = WelcomePages.all.filter { it.choosesReminderHour }

        assertEquals(listOf(WelcomePages.all.last()), choosing)
    }
}
