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
 * **This ring measures nothing, and nothing in this app may make it.** Its one caller is
 * `TimerGauge`, whose sweep is motion rather than measurement. The ring fills that once graded a
 * figure against a baseline — the month's days-shown-up ratio, the longest day, the best run — are
 * deleted, and a fill drawn against the user's own best ratchets: beat it once and every ordinary
 * day behind it re-renders as a partial version of that one. Presence may be measured; hours may
 * not. See the no-grading rule in CLAUDE.md before adding a second caller.
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
