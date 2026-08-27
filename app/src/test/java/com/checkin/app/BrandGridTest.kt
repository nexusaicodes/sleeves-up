package com.checkin.app

import com.checkin.app.ui.components.cellProgress
import com.checkin.app.ui.components.waveLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settle's schedule. It is one cursor over seven cells rather than seven animations precisely so
 * the ordering is a fact a test can hold, and these are the cases the drawing would otherwise get
 * wrong silently: a cell that starts before its turn, one that never finishes, and a cursor at rest
 * leaving anything short of fully arrived.
 */
class BrandGridTest {

    @Test
    fun `nothing has arrived at the start`() {
        assertEquals(0f, cellProgress(0, 0f), 0f)
        assertEquals(0f, cellProgress(6, 0f), 0f)
    }

    @Test
    fun `a later cell has not begun while an earlier one is still arriving`() {
        // 100ms in: cell 0 (starts at 0) is part way, cell 2 (starts at 140) has not begun.
        assertTrue(cellProgress(0, 100f) > 0f)
        assertEquals(0f, cellProgress(2, 100f), 0f)
    }

    @Test
    fun `cells arrive in index order`() {
        val at = 200f
        val progress = (0..6).map { cellProgress(it, at) }
        assertEquals(progress, progress.sortedDescending())
    }

    @Test
    fun `every cell is fully arrived once the cursor is settled`() {
        // The cursor's end value: the last cell's offset plus its own duration.
        val settled = (6 * 70 + 260).toFloat()
        (0..6).forEach { assertEquals(1f, cellProgress(it, settled), 0f) }
    }

    @Test
    fun `progress never exceeds one or falls below zero`() {
        assertEquals(1f, cellProgress(0, 10_000f), 0f)
        assertEquals(0f, cellProgress(3, -50f), 0f)
    }

    // --- the gauge's wave --------------------------------------------------------------------

    @Test
    fun `every cell traces the same curve a beat apart`() {
        // Cell n at phase p is cell 0 at phase p minus n sevenths: identical curve, shifted. A wave
        // whose cells differed would be a ranking of cells, which the mark must not carry.
        (0 until FILLED).forEach { index ->
            val shifted = index.toFloat() / FILLED
            assertEquals(waveLevel(0, 0.25f), waveLevel(index, 0.25f + shifted), TOLERANCE)
        }
    }

    @Test
    fun `the wave stays within the track and the full colour`() {
        (0..100).forEach { step ->
            val phase = step / 100f
            (0 until FILLED).forEach { index ->
                val level = waveLevel(index, phase)
                assertTrue("cell $index at $phase gave $level", level in 0f..1f)
            }
        }
    }

    @Test
    fun `the pass wraps rather than stopping at the end`() {
        (0 until FILLED).forEach { index ->
            assertEquals(waveLevel(index, 0f), waveLevel(index, 1f), TOLERANCE)
            assertEquals(waveLevel(index, 0.3f), waveLevel(index, 1.3f), TOLERANCE)
        }
    }

    @Test
    fun `no two cells peak together`() {
        val peaks = (0 until FILLED).map { index ->
            (0..999).maxBy { waveLevel(index, it / 1000f) }
        }
        assertEquals(FILLED, peaks.toSet().size)
    }

    private companion object {
        const val HOUR = 3_600_000L

        /** Cells the mark fills: seven of nine. */
        const val FILLED = 7
        const val TOLERANCE = 1e-4f
    }
}
