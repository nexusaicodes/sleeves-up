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

    /** A zero id is an unresolved reference, which renders as a crash rather than as a blank page. */
    @Test
    fun `every page names both of its strings`() {
        assertEquals(emptyList<Int>(), WelcomePages.all.filter { it.titleRes == 0 || it.bodyRes == 0 })
    }
}
