package com.checkin.app.ui.components.charts

import com.checkin.app.ui.components.charts.ChartGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {

    private val tolerance = 0.001f

    @Test
    fun `an empty series draws nothing`() {
        assertTrue(ChartGeometry.donutSegments(emptyList()).isEmpty())
    }

    /** A month with no tracked days must not divide by zero — it renders the bare ring instead. */
    @Test
    fun `an all-zero series draws nothing`() {
        assertTrue(ChartGeometry.donutSegments(listOf(0f, 0f, 0f)).isEmpty())
    }

    @Test
    fun `a single value fills the whole circle`() {
        val segments = ChartGeometry.donutSegments(listOf(5f))

        assertEquals(1, segments.size)
        assertEquals(ChartGeometry.TOP_OF_CIRCLE, segments[0].startAngle, tolerance)
        assertEquals(360f, segments[0].sweepAngle, tolerance)
    }

    @Test
    fun `values split proportionally`() {
        val segments = ChartGeometry.donutSegments(listOf(1f, 1f, 2f))

        assertEquals(listOf(0, 1, 2), segments.map { it.index })
        assertEquals(90f, segments[0].sweepAngle, tolerance)
        assertEquals(90f, segments[1].sweepAngle, tolerance)
        assertEquals(180f, segments[2].sweepAngle, tolerance)
    }

    /** Zero slices are skipped, but the surviving segments keep their original colour indices. */
    @Test
    fun `zero values are skipped without shifting colour indices`() {
        val segments = ChartGeometry.donutSegments(listOf(3f, 0f, 1f))

        assertEquals(listOf(0, 2), segments.map { it.index })
        assertEquals(270f, segments[0].sweepAngle, tolerance)
        assertEquals(90f, segments[1].sweepAngle, tolerance)
    }

    @Test
    fun `segments are contiguous and close the circle exactly`() {
        val segments = ChartGeometry.donutSegments(listOf(1f, 1f, 1f))

        segments.zipWithNext { a, b ->
            assertEquals(a.startAngle + a.sweepAngle, b.startAngle, tolerance)
        }
        val last = segments.last()
        assertEquals(
            ChartGeometry.TOP_OF_CIRCLE + 360f,
            last.startAngle + last.sweepAngle,
            tolerance,
        )
        assertEquals(360f, segments.sumOf { it.sweepAngle.toDouble() }.toFloat(), tolerance)
    }

    /** Thirds don't divide evenly into 360 in float maths; the last segment absorbs the drift. */
    @Test
    fun `uneven divisions still sum to a full turn`() {
        val segments = ChartGeometry.donutSegments(listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f))

        assertEquals(360f, segments.sumOf { it.sweepAngle.toDouble() }.toFloat(), tolerance)
    }

    @Test
    fun `negative values are ignored`() {
        val segments = ChartGeometry.donutSegments(listOf(-4f, 2f))

        assertEquals(listOf(1), segments.map { it.index })
        assertEquals(360f, segments[0].sweepAngle, tolerance)
    }

    // --- niceMaxY ---

    /** An all-zero series must still yield a usable divisor rather than scaling by zero. */
    @Test
    fun `niceMaxY never returns zero`() {
        assertEquals(1f, ChartGeometry.niceMaxY(0f), tolerance)
        assertEquals(1f, ChartGeometry.niceMaxY(-5f), tolerance)
    }

    @Test
    fun `niceMaxY rounds up to a readable step`() {
        assertEquals(2f, ChartGeometry.niceMaxY(1.4f), tolerance)
        assertEquals(5f, ChartGeometry.niceMaxY(4.2f), tolerance)
        assertEquals(10f, ChartGeometry.niceMaxY(8.1f), tolerance)
        assertEquals(20f, ChartGeometry.niceMaxY(11f), tolerance)
    }

    @Test
    fun `niceMaxY is exact on an already-round value`() {
        assertEquals(10f, ChartGeometry.niceMaxY(10f), tolerance)
    }

    // --- linePoints ---

    @Test
    fun `linePoints on an empty series is empty`() {
        assertTrue(ChartGeometry.linePoints(emptyList(), 100f, 50f, 10f).isEmpty())
    }

    /** A lone reading is centred; pinned left it would read as a truncated series. */
    @Test
    fun `a single point is centred horizontally`() {
        val points = ChartGeometry.linePoints(listOf(5f), 100f, 50f, 10f)

        assertEquals(1, points.size)
        assertEquals(50f, points[0].x, tolerance)
        assertEquals(25f, points[0].y, tolerance)
    }

    @Test
    fun `linePoints spans the full width and inverts y`() {
        val points = ChartGeometry.linePoints(listOf(0f, 5f, 10f), 100f, 50f, 10f)

        assertEquals(0f, points[0].x, tolerance)
        assertEquals(100f, points[2].x, tolerance)
        assertEquals(50f, points[0].y, tolerance) // zero sits on the baseline
        assertEquals(0f, points[2].y, tolerance) // the max touches the top
    }

    /** A value above the chart's ceiling clamps to the top rather than drawing outside the canvas. */
    @Test
    fun `values above the ceiling clamp to the top edge`() {
        val points = ChartGeometry.linePoints(listOf(99f), 100f, 50f, 10f)

        assertEquals(0f, points[0].y, tolerance)
    }

    @Test
    fun `a zero ceiling does not divide by zero`() {
        val points = ChartGeometry.linePoints(listOf(0f, 0f), 100f, 50f, 0f)

        points.forEach { assertEquals(50f, it.y, tolerance) }
    }

    // --- barRects ---

    @Test
    fun `barRects on an empty series is empty`() {
        assertTrue(ChartGeometry.barRects(emptyList(), 100f, 50f, 10f).isEmpty())
    }

    @Test
    fun `bars are evenly spaced and sit on the baseline`() {
        val bars = ChartGeometry.barRects(listOf(10f, 5f), 100f, 50f, 10f, gapRatio = 0f)

        assertEquals(2, bars.size)
        assertEquals(0f, bars[0].left, tolerance)
        assertEquals(50f, bars[0].right, tolerance)
        assertEquals(100f, bars[1].right, tolerance)
        bars.forEach { assertEquals(50f, it.bottom, tolerance) }
        assertEquals(0f, bars[0].top, tolerance) // full height
        assertEquals(25f, bars[1].top, tolerance) // half height
    }

    @Test
    fun `the gap ratio narrows bars within their slot`() {
        val bars = ChartGeometry.barRects(listOf(1f, 1f), 100f, 50f, 1f, gapRatio = 0.5f)

        assertEquals(25f, bars[0].right - bars[0].left, tolerance)
        // Still centred in its 50-wide slot.
        assertEquals(12.5f, bars[0].left, tolerance)
    }

    /** A zero-height bar would otherwise invert and draw upward off the baseline. */
    @Test
    fun `a zero value produces a bar with no height`() {
        val bars = ChartGeometry.barRects(listOf(0f), 100f, 50f, 10f)

        assertEquals(bars[0].bottom, bars[0].top, tolerance)
    }
}
