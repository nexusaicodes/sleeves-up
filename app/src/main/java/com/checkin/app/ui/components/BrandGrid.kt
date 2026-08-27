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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.checkin.app.ui.theme.CheckInAppTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow

/**
 * The brand mark, drawn rather than loaded, so its cells can be addressed individually.
 *
 * The geometry is the one in `play-store-assets/generate_icons.py` — a 3x3 lattice of rounded cells
 * at [FILL_FRAC] of the pitch, with [EMPTY_CELLS] left out. Seven of nine: a full grid claims a
 * perfect month, which is the claim this app exists not to make. **Those constants are a second
 * copy of the generator's and must be changed with it**; the drawables it emits are the launcher,
 * splash and notification marks, and this is the same mark at display size. It is a copy rather
 * than a shared source because the generator is Python and emits `pathData` — a single flattened
 * path, which is exactly what cannot be animated per cell.
 *
 * [settle] runs the arrival once and then rests: the cells land in reading order and the last one
 * overshoots slightly, so a mark that was already there reads as a mark that has just gained an
 * entry. It never repeats and there is nothing to retire — see [CheckOutCelebration] for why the
 * one moment it plays in is the one moment the app has to spend on motion. With the system
 * animation scale off it is drawn at rest immediately, like every other animation in the app.
 */
@Composable
fun BrandGrid(color: Color, modifier: Modifier = Modifier, settle: Boolean = false) {
    val animated = animationsEnabled()
    val cursor = remember(settle) { Animatable(if (settle) 0f else SETTLED) }

    LaunchedEffect(settle, animated) {
        if (settle && animated) {
            cursor.animateTo(SETTLED, tween(settleDurationMs(), easing = LinearEasing))
        } else {
            cursor.snapTo(SETTLED)
        }
    }

    val elapsed = cursor.value

    GridCanvas(modifier) { index ->
        val progress = cellProgress(index, elapsed)
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
    color: Color = MaterialTheme.colorScheme.primary,
    settle: Boolean = false,
) = BrandGrid(color = color, modifier = modifier.size(size), settle = settle)

/**
 * The mark as the open session's state: a wave of brightness travelling the seven cells, or the
 * whole grid at [trackColor] when nothing is running.
 *
 * **No cell has a position that means anything, and that is the point of this shape.** Every cell
 * does the same thing a beat apart, so there is no current cell, nothing to be part of the way
 * through, and nothing a later reader could be tempted to give a meaning to. Two shapes are
 * therefore closed to this gauge. **A single cell lit and advanced on a schedule** encodes how far
 * through that schedule the session is — a figure nothing here is permitted to read, and one slow
 * enough to be invisible at any period long enough to be calm. **A lattice lighting cell after cell
 * until it is full** is a completion bar, which is the deleted daily target as geometry, and it
 * would turn the mark's own two gaps into a shortfall rather than the honest part.
 *
 * What is left is the binary, which is the whole of what this gauge says: the mark is alive while a
 * session is open and still while none is. It is the idiom [OngoingPulse] already uses for exactly
 * that claim, one file over, so the two read as the same statement at two sizes.
 *
 * With the system animation scale off the running state is drawn as every cell in [color] — flat,
 * but still unmistakably not the rest state, which is the one property that must survive.
 */
@Composable
fun BrandGridGauge(running: Boolean, color: Color, trackColor: Color, modifier: Modifier = Modifier) {
    val animated = animationsEnabled()
    // The transition is composed only while it has something to drive. Started unconditionally it
    // keeps ticking behind the rest state, recomposing this canvas every frame to redraw the same
    // seven track-coloured cells — on the tab the app opens on, for as long as it is open.
    val waving = running && animated
    val phase = if (waving) wavePhase() else 0f

    GridCanvas(modifier) { index ->
        val cellColor = when {
            !running -> trackColor
            !waving -> color
            else -> lerp(trackColor, color, waveLevel(index, phase))
        }
        CellPaint(cellColor, scale = 1f)
    }
}

/** The pass's position, 0..1, restarting each lap. */
@Composable
private fun wavePhase(): Float {
    val transition = rememberInfiniteTransition(label = "gauge")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(WAVE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gauge-wave",
    )
    return phase
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

        filledCells().forEachIndexed { index, (row, col) ->
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

// --- geometry, mirroring generate_icons.py -------------------------------------------------

private const val CELLS_A_SIDE = 3
private const val FILL_FRAC = 0.88f
private const val CORNER_FRAC = 0.22f

/** Row/column of the two cells the mark leaves out. Off the diagonal and off centre on purpose. */
private val EMPTY_CELLS = setOf(1 to 2, 2 to 0)
private const val EMPTY_COUNT = 2

private const val FILLED_COUNT = CELLS_A_SIDE * CELLS_A_SIDE - EMPTY_COUNT

private fun filledCells(): List<Pair<Int, Int>> = (0 until CELLS_A_SIDE).flatMap { row ->
    (0 until CELLS_A_SIDE).map { col -> row to col }
}.filterNot { it in EMPTY_CELLS }

// --- the settle ----------------------------------------------------------------------------

/** Milliseconds one cell takes to arrive, and the offset between one cell's start and the next. */
private const val CELL_MS = 260
private const val STAGGER_MS = 70

/** The cursor's end value: the last cell's start offset plus its own duration, in milliseconds. */
private val SETTLED = ((FILLED_COUNT - 1) * STAGGER_MS + CELL_MS).toFloat()

private fun settleDurationMs(): Int = SETTLED.toInt()

/**
 * How far the cell at [index] has arrived when the settle is [elapsed] milliseconds in.
 *
 * Pure, and the reason the animation is one `Animatable` rather than seven: a cursor running over a
 * fixed schedule cannot let two cells disagree about where in the sequence they are.
 */
internal fun cellProgress(index: Int, elapsed: Float): Float =
    ((elapsed - index * STAGGER_MS) / CELL_MS).coerceIn(0f, 1f)

private const val MIN_SCALE = 0.6f

// --- the gauge -----------------------------------------------------------------------------

/**
 * One pass of the wave over the seven cells — so the band steps to the next cell every seventh of
 * this, and any one cell peaks once per pass.
 *
 * Deliberately calmer than `OngoingPulse`'s 600ms cycle, which is a 6dp glyph in a table row and has
 * to catch the eye; this is a 200dp mark under a number counting hours, and it is on screen for as
 * long as the tab is. **There is a ceiling as well as a floor**: past roughly five seconds the cells
 * drift far enough apart in phase to stop reading as one wave and start reading as cells fading
 * independently, which loses the single-living-thing impression the shape exists for.
 */
private const val WAVE_MS = 3_000

private const val TWO_PI = 2.0 * Math.PI

/**
 * How bright the cell at [index] is when the wave is [phase] of the way through a pass, on 0..1
 * from the track colour to the full one.
 *
 * A raised cosine offset by the cell's share of the lattice, so each cell peaks a beat after the
 * one before it and every cell traces the identical curve. **That every cell is identical is the
 * property worth keeping**: a wave whose cells differed in height or dwell would be a ranking of
 * cells, and a ranking is the one thing the mark must not carry.
 */
internal fun waveLevel(index: Int, phase: Float): Float {
    val offset = phase - index.toFloat() / FILLED_COUNT
    val cellPhase = offset - floor(offset)
    val raisedCosine = (cos(TWO_PI * cellPhase) + 1.0) / 2.0
    return raisedCosine.pow(CREST_POWER).toFloat()
}

/**
 * How narrow the crest is. A plain raised cosine (1.0) spreads the bright band over about three
 * cells and reads as a swell; raising it contracts the band and lengthens the dark stretch behind
 * it, so the pass reads as a pulse travelling the lattice instead. It changes the shape of the
 * curve every cell traces, never which cell traces which — the cells stay identical, which is the
 * property that keeps the wave from ranking them.
 */
private const val CREST_POWER = 2.0

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
