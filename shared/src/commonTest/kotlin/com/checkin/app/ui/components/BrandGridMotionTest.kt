package com.checkin.app.ui.components

import com.checkin.app.ui.components.BrandGridMotion.cellProgress
import com.checkin.app.ui.components.BrandGridMotion.waveLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settle's schedule and the gauge's wave. The settle is one cursor over seven cells precisely so
 * the ordering is a fact a test can hold; the wave must give every cell the identical curve.
 */
class BrandGridMotionTest {

    @Test
    fun `the mark fills seven of nine cells`() {
        assertEquals(FILLED, BrandGridMotion.FILLED_COUNT)
        assertTrue(BrandGridMotion.FILLED_CELLS.none { it in BrandGridMotion.EMPTY_CELLS })
    }

    @Test
    fun `nothing has arrived at the start`() {
        assertEquals(0f, cellProgress(0, 0f))
        assertEquals(0f, cellProgress(6, 0f))
    }

    @Test
    fun `a later cell has not begun while an earlier one is still arriving`() {
        // 100ms in: cell 0 (starts at 0) is part way, cell 2 (starts at 140) has not begun.
        assertTrue(cellProgress(0, 100f) > 0f)
        assertEquals(0f, cellProgress(2, 100f))
    }

    @Test
    fun `cells arrive in index order`() {
        val progress = (0..6).map { cellProgress(it, 200f) }
        assertEquals(progress, progress.sortedDescending())
    }

    @Test
    fun `every cell is fully arrived once the cursor is settled`() {
        // The last cell's offset plus its own duration.
        assertEquals((6 * 70 + 260).toFloat(), BrandGridMotion.SETTLED_MS)
        (0..6).forEach { assertEquals(1f, cellProgress(it, BrandGridMotion.SETTLED_MS)) }
    }

    @Test
    fun `progress never exceeds one or falls below zero`() {
        assertEquals(1f, cellProgress(0, 10_000f))
        assertEquals(0f, cellProgress(3, -50f))
    }

    @Test
    fun `every cell traces the same curve a beat apart`() {
        // Cell n at phase p is cell 0 at phase p minus n sevenths: identical curve, shifted.
        (0 until FILLED).forEach { index ->
            val shifted = index.toFloat() / FILLED
            assertClose(waveLevel(0, 0.25f), waveLevel(index, 0.25f + shifted))
        }
    }

    @Test
    fun `the wave stays within the track and the full colour`() {
        (0..100).forEach { step ->
            val phase = step / 100f
            (0 until FILLED).forEach { index ->
                val level = waveLevel(index, phase)
                assertTrue(level in 0f..1f, "cell $index at $phase gave $level")
            }
        }
    }

    @Test
    fun `the pass wraps rather than stopping at the end`() {
        (0 until FILLED).forEach { index ->
            assertClose(waveLevel(index, 0f), waveLevel(index, 1f))
            assertClose(waveLevel(index, 0.3f), waveLevel(index, 1.3f))
        }
    }

    @Test
    fun `no two cells peak together`() {
        val peaks = (0 until FILLED).map { index ->
            (0..999).maxBy { waveLevel(index, it / 1000f) }
        }
        assertEquals(FILLED, peaks.toSet().size)
    }

    private fun assertClose(expected: Float, actual: Float) =
        assertTrue(abs(expected - actual) < TOLERANCE, "expected $expected, got $actual")

    private companion object {
        const val FILLED = 7
        const val TOLERANCE = 1e-4f
    }
}
