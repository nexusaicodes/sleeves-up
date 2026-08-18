package com.checkin.app

import com.checkin.app.ui.navigation.Screen
import com.checkin.app.ui.navigation.allScreens
import com.checkin.app.ui.navigation.tabs
import com.checkin.app.ui.navigation.titledScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nav hierarchy fails silently rather than loudly: an unregistered route renders under the start
 * destination's title, and a duplicate route collides in the saved back stack, which is keyed by
 * `route.hashCode()`. Neither throws, so both are pinned here.
 */
class ScreenTest {

    @Test
    fun `every route is unique`() {
        val routes = allScreens.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `every screen carries a title`() {
        assertTrue(allScreens.all { it.titleRes != 0 })
    }

    @Test
    fun `the title bar can name every destination`() {
        assertEquals(allScreens.toSet(), titledScreens.toSet())
    }

    @Test
    fun `every tab is a titled destination, in declaration order`() {
        assertTrue(titledScreens.containsAll(tabs))
        assertEquals(tabs, allScreens.filterIsInstance<Screen.Tab>())
    }

    /** A detail parented to something the bottom bar does not draw would leave no tab lit. */
    @Test
    fun `every detail's parent is a tab`() {
        val details = allScreens.filterIsInstance<Screen.Detail>()
        assertTrue(details.isNotEmpty())
        assertTrue(details.all { it.parent in tabs })
    }
}
