package com.checkin.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Google-Clock-style gauge: a faint full track with a rounded-cap progress arc sweeping clockwise
 * from the top. [content] is centered inside the ring. Purely presentational — the caller owns the
 * [progress] value (coerced to 0f..1f here).
 *
 * [contentDescription] must state how far along the arc is wherever the arc means something: the fill
 * and its colour are the only things carrying that, and neither reaches a screen reader. It goes on
 * the arc rather than the whole ring so [content] keeps announcing itself.
 *
 * A [progress] of zero draws the track alone, which callers also use deliberately as a **frame** — a
 * ring around a figure that measures nothing. Those state the figure instead, and pass a thinner
 * stroke and a neutral colour so it does not read as a gauge stuck at zero.
 */
@Composable
fun CircularProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription },
        ) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}
