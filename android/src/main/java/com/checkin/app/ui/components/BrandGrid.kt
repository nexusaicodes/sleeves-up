package com.checkin.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adevinta.spark.SparkTheme
import com.checkin.app.ui.components.BrandGridMotion.CELLS_A_SIDE
import com.checkin.app.ui.components.BrandGridMotion.CORNER_FRAC
import com.checkin.app.ui.components.BrandGridMotion.FILLED_CELLS
import com.checkin.app.ui.components.BrandGridMotion.FILLED_COUNT
import com.checkin.app.ui.components.BrandGridMotion.FILL_FRAC
import com.checkin.app.ui.components.BrandGridMotion.MIN_SCALE
import com.checkin.app.ui.components.BrandGridMotion.WAVE_MS
import com.checkin.app.ui.components.BrandGridMotion.cellProgress
import com.checkin.app.ui.components.BrandGridMotion.waveLevel
import com.checkin.app.ui.theme.CheckInAppTheme

/**
 * The brand mark, drawn rather than loaded, so its cells can be addressed individually.
 *
 * The geometry, the settle's schedule and the gauge's wave are numbers in the shared
 * [BrandGridMotion], which iOS's mark draws from too; this file only turns them into draws.
 *
 * [settle] runs the arrival once and then rests: the cells land in reading order and the last one
 * overshoots slightly, so a mark that was already there reads as a mark that has just gained an
 * entry. It never repeats and there is nothing to retire; its one caller is the check-out
 * celebration, the one moment the app spends on motion. With the system
 * animation scale off it is drawn at rest immediately, like every other animation in the app.
 */
@Composable
fun BrandGrid(color: Color, modifier: Modifier = Modifier, settle: Boolean = false) {
    val animated = animationsEnabled()
    // Keyed on `animated` as well as `settle`: seeded at 0f with the system animation scale off,
    // the first draw would find every cell at zero progress and paint nothing, and whether that
    // was ever visible would turn on the effect landing before the traversal.
    val cursor = remember(settle, animated) { Animatable(if (settle && animated) 0f else SETTLED) }

    LaunchedEffect(settle, animated) {
        if (settle && animated) {
            cursor.animateTo(SETTLED, tween(settleDurationMs(), easing = LinearEasing))
        } else {
            cursor.snapTo(SETTLED)
        }
    }

    val elapsed = cursor.asState()

    GridCanvas(modifier) { index ->
        val progress = cellProgress(index, elapsed.value)
        if (progress <= 0f) {
            null
        } else {
            // The last cell in reading order is the one that has just been added, so it lands with
            // the overshoot and the rest simply arrive.
            val eased = if (index == FILLED_COUNT - 1) {
                EaseOutBack.transform(progress)
            } else {
                EaseOutCubic.transform(progress)
            }
            CellPaint(
                color = color.copy(alpha = EaseOutCubic.transform(progress)),
                scale = MIN_SCALE + (1f - MIN_SCALE) * eased,
            )
        }
    }
}

/** Convenience for the common case: the mark at a fixed side, tinted with the brand colour. */
@Composable
fun BrandGrid(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = SparkTheme.colors.main,
    settle: Boolean = false,
) = BrandGrid(color = color, modifier = modifier.size(size), settle = settle)

/**
 * The mark as the open session's state: a wave of brightness travelling the seven cells, or the
 * whole grid at [trackColor] when nothing is running.
 *
 * **No cell has a position that means anything, and that is the point of this shape.** Every cell
 * does the same thing a beat apart, so there is no current cell and nothing to be part of the way
 * through. Two shapes are therefore closed to this gauge: **a single cell lit and advanced on a
 * schedule** encodes how far through that schedule the session is, a figure nothing here may read;
 * and **a lattice lighting cell after cell until it is full** is a completion bar — a daily target
 * as geometry, turning the mark's own two gaps into a shortfall.
 *
 * What is left is the binary: the mark is alive while a session is open and still while none is.
 * It is the idiom `OngoingPulse` uses for the same claim, so the two read as one statement at two
 * sizes.
 *
 * With the system animation scale off the running state is drawn as every cell in [color] — flat,
 * but still unmistakably not the rest state, which is the one property that must survive.
 */
@Composable
fun BrandGridPulse(running: Boolean, color: Color, trackColor: Color, modifier: Modifier = Modifier) {
    val animated = animationsEnabled()
    // Null rather than a settled value: the transition is composed only while it has something to
    // drive, so nothing redraws the rest state's seven track-coloured cells sixty times a second on
    // the tab the app opens on. Running, the phase is read in the draw lambda alone.
    val phase = if (running && animated) wavePhase() else null

    GridCanvas(modifier) { index ->
        val cellColor = when {
            !running -> trackColor
            phase == null -> color
            else -> lerp(trackColor, color, waveLevel(index, phase.value))
        }
        CellPaint(cellColor, scale = 1f)
    }
}

/**
 * The pass's position, 0..1, restarting each lap.
 *
 * Returned as the `State` rather than as its value, so that reading it inside the draw lambda
 * invalidates the draw alone. Read here in composition it would recompose the whole gauge subtree
 * sixty times a second for as long as a session is open, which is the cost this shape avoids.
 */
@Composable
private fun wavePhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "gauge")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(WAVE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gauge-wave",
    )
}

/** How one cell is painted this frame. A null from [paint] leaves the cell undrawn entirely. */
private data class CellPaint(val color: Color, val scale: Float)

/**
 * The lattice itself: the single place the mark's geometry is turned into draws, so the settle and
 * the gauge cannot disagree about where a cell is or how round its corners are.
 */
@Composable
private fun GridCanvas(modifier: Modifier, paint: (Int) -> CellPaint?) {
    Canvas(modifier = modifier) {
        val pitch = size.minDimension / CELLS_A_SIDE
        val cell = pitch * FILL_FRAC
        val inset = (pitch - cell) / 2f
        val originX = (size.width - pitch * CELLS_A_SIDE) / 2f
        val originY = (size.height - pitch * CELLS_A_SIDE) / 2f
        val radius = CornerRadius(cell * CORNER_FRAC)

        FILLED_CELLS.forEachIndexed { index, (row, col) ->
            val cellPaint = paint(index) ?: return@forEachIndexed
            val scaled = cell * cellPaint.scale
            val slack = (cell - scaled) / 2f

            drawRoundRect(
                color = cellPaint.color,
                topLeft = Offset(
                    originX + col * pitch + inset + slack,
                    originY + row * pitch + inset + slack,
                ),
                size = Size(scaled, scaled),
                cornerRadius = radius * cellPaint.scale,
                // No `alpha` argument: it multiplies with the colour's own, so passing both would
                // square it — the 0.15 track would land at 0.02 and read as an empty canvas.
            )
        }
    }
}

/** The settle's cursor at rest, so every cell is drawn fully arrived. */
private val SETTLED = BrandGridMotion.SETTLED_MS

private fun settleDurationMs(): Int = SETTLED.toInt()

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BrandGridPreview() {
    CheckInAppTheme {
        Box(modifier = Modifier.padding(24.dp)) {
            BrandGrid(size = 72.dp)
        }
    }
}
