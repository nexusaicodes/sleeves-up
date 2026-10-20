package com.checkin.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A filled line chart over [values], scaled to a rounded ceiling derived from the data and
 * [referenceValue] together — so the reference line is always on screen and the tallest day
 * doesn't clip.
 *
 * @param referenceValue optional horizontal marker, drawn dashed. Reports pass the window's own
 *   mean: it shows where the window sits relative to itself, never a bar a day could fall short of.
 */
@Composable
fun LineChart(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Unspecified,
    fillColor: Color = Color.Unspecified,
    referenceValue: Float? = null,
    referenceColor: Color = Color.Unspecified,
    strokeWidth: Dp = 2.dp,
) {
    // A Canvas does not mirror, but the axis labels under it are a Row, which does. Under a
    // right-to-left language time therefore runs right to left here too — the first point at the start
    // edge, as the calendar and the label beneath it already are.
    val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Canvas(
        modifier = modifier
            .graphicsLayer { scaleX = mirror }
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (values.isEmpty()) return@Canvas

        val ceiling = ChartGeometry.niceMaxY(
            maxOf(values.maxOrNull() ?: 0f, referenceValue ?: 0f),
        )
        // The stroke is centred on the path, so a value sitting exactly on the floor or the ceiling
        // would lose its outer half to the canvas edge. A window of all-zero days is the case that
        // matters: drawn flush to the bottom it reads as a blank chart rather than as a flat zero,
        // which is a real answer the reader is owed. Inset by a full stroke and draw inside that.
        val stroke = strokeWidth.toPx()
        val plotHeight = (size.height - stroke).coerceAtLeast(0f)
        val plotTop = stroke / 2f
        val points = ChartGeometry.linePoints(values, size.width, plotHeight, ceiling)
            .map { ChartGeometry.Point(it.x, it.y + plotTop) }

        if (referenceValue != null && referenceValue > 0f) {
            val y = plotTop + plotHeight - (referenceValue / ceiling).coerceIn(0f, 1f) * plotHeight
            drawLine(
                color = referenceColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        // A single reading has no segment to stroke, so mark it as a dot instead of drawing nothing.
        if (points.size == 1) {
            drawCircle(lineColor, radius = stroke * 1.5f, center = Offset(points[0].x, points[0].y))
            return@Canvas
        }

        val baseline = plotTop + plotHeight
        val line = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, baseline)
            lineTo(points.first().x, baseline)
            close()
        }

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)),
        )
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
