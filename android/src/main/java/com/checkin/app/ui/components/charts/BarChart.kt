package com.checkin.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Rounded vertical bars over [values], scaled to a rounded ceiling derived from the data. Axis
 * labels belong to the caller, which knows what the bars represent.
 *
 * @param baselineColor the rule the bars stand on. It is drawn whether or not any bar has height,
 *   which is the point: a period with no hours in it is a fact worth stating, and without the rule
 *   the chart is an empty box that reads as a failure to render rather than as a row of zeros.
 */
@Composable
fun BarChart(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Unspecified,
    baselineColor: Color = Color.Unspecified,
    cornerRadius: Dp = 4.dp,
    baselineWidth: Dp = 1.dp,
) {
    // A Canvas does not mirror, but the axis labels under it are a Row, which does. Under a
    // right-to-left language time therefore runs right to left here too — the first bar at the start
    // edge, as the calendar and the label beneath it already are.
    val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Canvas(
        modifier = modifier
            .graphicsLayer { scaleX = mirror }
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (values.isEmpty()) return@Canvas

        val ceiling = ChartGeometry.niceMaxY(values.maxOrNull() ?: 0f)
        val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        // Bars are measured against a floor that leaves the rule its own width, so a full-height bar
        // stands on the baseline rather than covering it.
        val rule = baselineWidth.toPx()
        val plotHeight = (size.height - rule).coerceAtLeast(0f)

        drawRect(
            color = baselineColor,
            topLeft = Offset(0f, plotHeight),
            size = Size(size.width, rule),
        )

        ChartGeometry.barRects(values, size.width, plotHeight, ceiling).forEach { bar ->
            val height = bar.bottom - bar.top
            // A zero-height rounded rect renders as a stray lens shape; skip it entirely — the
            // baseline is what says "zero" for that slot.
            if (height <= 0f) return@forEach
            drawRoundRect(
                color = barColor,
                topLeft = Offset(bar.left, bar.top),
                size = Size(bar.right - bar.left, height),
                cornerRadius = radius,
            )
        }
    }
}
