package com.checkin.app.ui.components.charts

import kotlin.math.floor
import kotlin.math.log10

/**
 * Pure geometry for the hand-rolled charts. Kept free of Compose and Android types so the maths is
 * unit-testable — the Canvas composables in this package only translate these results into draws.
 */
object ChartGeometry {

    /** One arc of a donut, in the degree convention `drawArc` uses (0 = 3 o'clock, clockwise). */
    data class Segment(val index: Int, val startAngle: Float, val sweepAngle: Float)

    /** A point in canvas space: y grows downward, so a larger value sits nearer the top. */
    data class Point(val x: Float, val y: Float)

    /** A bar in canvas space, [top] being the value end and [bottom] the baseline. */
    data class Bar(val index: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

    private const val FULL_TURN = 360f

    /** Sweeps start at 12 o'clock and run clockwise. */
    const val TOP_OF_CIRCLE = -90f

    /**
     * Side of the largest square that fits inside a circle, as a fraction of its diameter (1/√2).
     *
     * What a ring's hole can actually hold. The hole is round, so its full diameter is only available
     * along the centre line — content bounded by the diameter still overhangs the arc at the top and
     * bottom of its own height. Applied inside [DonutChart] rather than at a call site, because
     * `CircularProgressRing` deliberately does *not* apply it — the Check-In gauge's 45sp clock is
     * wider than the square and must not wrap.
     *
     * Currently **dormant**: every donut in the app leaves its hole empty, so the bound has no live
     * caller. Kept anyway — the next caption to go in a hole is exactly the case it exists for, and
     * it lives here rather than in either drawing so the two cannot disagree about it again.
     */
    const val INSCRIBED_SQUARE = 0.707f

    private const val DECIMAL_BASE = 10.0

    /** The ladder [niceMaxY] rounds up to, as multiples of the value's order of magnitude. */
    private val NICE_STEPS = listOf(1f, 2f, 5f, 10f)

    /** A bar always keeps at least a tenth of its slot, so a wide gap can't erase it entirely. */
    private const val MAX_GAP_RATIO = 0.9f

    /**
     * Proportional arcs for [values], in order, starting at [startAngle]. Zero and negative values
     * are skipped rather than drawn as hairlines, and the final segment absorbs any rounding drift
     * so the arcs always close the full circle. Returns empty when nothing is positive.
     */
    fun donutSegments(values: List<Float>, startAngle: Float = TOP_OF_CIRCLE): List<Segment> {
        val total = values.filter { it > 0f }.sum()
        if (total <= 0f) return emptyList()

        val drawable = values.withIndex().filter { it.value > 0f }
        var cursor = startAngle
        return drawable.mapIndexed { position, (index, value) ->
            val sweep = if (position == drawable.lastIndex) {
                // Close exactly on the start angle instead of accumulating float error.
                startAngle + FULL_TURN - cursor
            } else {
                value / total * FULL_TURN
            }
            Segment(index, cursor, sweep).also { cursor += sweep }
        }
    }

    /**
     * A round number at or above [rawMax] to scale an axis to, so gridlines land on values a reader
     * can name (2, 5, 10, …) rather than 7.3. Never returns zero — an all-zero series still needs a
     * non-degenerate axis to divide by.
     */
    fun niceMaxY(rawMax: Float): Float {
        if (rawMax <= 0f) return 1f
        val magnitude = Math.pow(DECIMAL_BASE, floor(log10(rawMax.toDouble()))).toFloat()
        val normalized = rawMax / magnitude
        val step = NICE_STEPS.firstOrNull { normalized <= it } ?: NICE_STEPS.last()
        return step * magnitude
    }

    /**
     * Maps [values] onto a [width] x [height] box against a [maxY] ceiling. A lone value is centred
     * rather than pinned to the left edge, where it would read as the start of a missing series.
     */
    fun linePoints(values: List<Float>, width: Float, height: Float, maxY: Float): List<Point> {
        if (values.isEmpty()) return emptyList()
        val ceiling = if (maxY > 0f) maxY else 1f
        if (values.size == 1) {
            return listOf(Point(width / 2f, yFor(values[0], height, ceiling)))
        }
        val step = width / (values.size - 1)
        return values.mapIndexed { i, v -> Point(i * step, yFor(v, height, ceiling)) }
    }

    /**
     * Evenly spaced bars across [width]. [gapRatio] is the share of each slot left as spacing, so
     * 0f yields a solid histogram and 0.5f yields bars half as wide as their slot.
     */
    fun barRects(values: List<Float>, width: Float, height: Float, maxY: Float, gapRatio: Float = 0.3f): List<Bar> {
        if (values.isEmpty()) return emptyList()
        val ceiling = if (maxY > 0f) maxY else 1f
        val slot = width / values.size
        val barWidth = slot * (1f - gapRatio.coerceIn(0f, MAX_GAP_RATIO))
        val inset = (slot - barWidth) / 2f
        return values.mapIndexed { i, v ->
            val left = i * slot + inset
            Bar(i, left, yFor(v, height, ceiling), left + barWidth, height)
        }
    }

    private fun yFor(value: Float, height: Float, ceiling: Float): Float =
        height - (value / ceiling).coerceIn(0f, 1f) * height
}
