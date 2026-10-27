package com.checkin.app

import com.checkin.app.widget.HomeWidgets
import com.checkin.app.widget.WidgetFace
import com.checkin.app.widget.WidgetSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSyncTest {

    private class FakeHomeWidgets(var isPlaced: Boolean = true) : HomeWidgets {
        val shown = mutableListOf<WidgetFace>()
        var failNextShow = false

        override fun animationsOn() = flowOf(true)

        override fun placed() = isPlaced

        override fun show(face: WidgetFace): Boolean {
            if (failNextShow) {
                failNextShow = false
                error("launcher unavailable")
            }
            if (!isPlaced) return false
            shown += face
            return true
        }

        override fun canRequestPin() = false

        override fun requestPin() = false
    }

    private val single = WidgetFace.Single("Writing")
    private val running = WidgetFace.Running("Writing")

    private fun TestScope.syncOver(widgets: FakeHomeWidgets, faces: () -> Flow<WidgetFace>) =
        WidgetSync(widgets, faces, backgroundScope, retryDelayMs = 1_000L)

    @Test
    fun `the collector draws each change once, skipping identical faces`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val faces = MutableStateFlow<WidgetFace>(single)
        val sync = syncOver(widgets) { faces }

        sync.start()
        faces.value = running
        faces.value = running
        faces.value = single

        assertEquals(listOf(single, running, single), widgets.shown)
    }

    @Test
    fun `an unforced redraw of the face already drawn costs no update`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val faces = MutableStateFlow<WidgetFace>(single)
        val sync = syncOver(widgets) { faces }

        sync.start()
        sync.redraw(force = false)

        assertEquals(listOf(single), widgets.shown)
    }

    @Test
    fun `a forced redraw draws the same face again`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val faces = MutableStateFlow<WidgetFace>(single)
        val sync = syncOver(widgets) { faces }

        sync.start()
        sync.redraw(force = true)

        assertEquals(listOf(single, single), widgets.shown)
    }

    @Test
    fun `with no widget placed nothing follows the tables and an unforced redraw does nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val widgets = FakeHomeWidgets(isPlaced = false)
            var subscriptions = 0
            val sync = syncOver(widgets) {
                subscriptions++
                MutableStateFlow(single)
            }

            sync.start()
            sync.redraw(force = false)

            assertFalse(sync.isSyncing)
            assertEquals(0, subscriptions)
            assertTrue(widgets.shown.isEmpty())
        }

    @Test
    fun `placing a widget starts the sync again`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets(isPlaced = false)
        val faces = MutableStateFlow<WidgetFace>(single)
        val sync = syncOver(widgets) { faces }

        sync.start()
        widgets.isPlaced = true
        sync.start()
        faces.value = running

        assertTrue(sync.isSyncing)
        assertEquals(listOf(single, running), widgets.shown)
    }

    @Test
    fun `a failed read re-subscribes rather than ending the sync`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val faces = MutableStateFlow<WidgetFace>(single)
        var attempts = 0
        val sync = syncOver(widgets) {
            flow {
                if (attempts++ == 0) error("database unavailable")
                faces.collect { emit(it) }
            }
        }

        sync.start()
        assertTrue(widgets.shown.isEmpty())
        advanceTimeBy(1_001L)
        runCurrent()
        faces.value = running

        assertTrue(sync.isSyncing)
        assertEquals(listOf(single, running), widgets.shown)
    }

    @Test
    fun `a failed draw is not remembered as drawn, so the next attempt draws it`() =
        runTest(UnconfinedTestDispatcher()) {
            val widgets = FakeHomeWidgets()
            val faces = MutableStateFlow<WidgetFace>(single)
            val sync = syncOver(widgets) { faces }

            widgets.failNextShow = true
            sync.start()
            sync.redraw(force = false)

            assertTrue(sync.isSyncing)
            assertEquals(listOf(single), widgets.shown)
        }

    @Test
    fun `a redraw whose read throws leaves the widget as it was`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val sync = syncOver(widgets) { flow { error("database unavailable") } }

        sync.redraw(force = true)

        assertTrue(widgets.shown.isEmpty())
    }

    @Test
    fun `a forced redraw with no widget placed reads nothing`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets(isPlaced = false)
        var subscriptions = 0
        val sync = syncOver(widgets) {
            subscriptions++
            MutableStateFlow(single)
        }

        sync.redraw(force = true)

        assertEquals(0, subscriptions)
        assertTrue(widgets.shown.isEmpty())
    }

    @Test
    fun `the collector can put back a face a redraw replaced`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val faces = MutableSharedFlow<WidgetFace>(replay = 1)
        var reads = 0
        val sync = syncOver(widgets) {
            if (reads++ == 1) flowOf(running) else faces
        }

        faces.emit(single)
        sync.start()
        sync.redraw(force = false)
        faces.emit(single)

        assertEquals(listOf(single, running, single), widgets.shown)
    }

    @Test
    fun `stop ends the sync`() = runTest(UnconfinedTestDispatcher()) {
        val widgets = FakeHomeWidgets()
        val faces = MutableStateFlow<WidgetFace>(single)
        val sync = syncOver(widgets) { faces }

        sync.start()
        sync.stop()
        faces.value = running

        assertFalse(sync.isSyncing)
        assertEquals(listOf(single), widgets.shown)
    }
}
