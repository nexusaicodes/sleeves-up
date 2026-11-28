package com.checkin.app.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow

/**
 * The brand mark's geometry and motion, as numbers, for both apps' drawings of it.
 *
 * The geometry is the one in `play-store-assets/generate_icons.py` — a 3x3 lattice of rounded cells
 * at [FILL_FRAC] of the pitch, with [EMPTY_CELLS] left out. Seven of nine: a full grid claims a
 * perfect month, which is the claim this app exists not to make. **These constants are a second copy
 * of the generator's and must be changed with it**; the generator emits flattened paths, which
 * cannot be addressed cell by cell. Android's `BrandGrid` and iOS's `BrandMark` only turn these
 * numbers into draws.
 */
object BrandGridMotion {

    /** One drawn cell's place in the lattice. */
    data class Cell(val row: Int, val col: Int)

    const val CELLS_A_SIDE = 3
    const val FILL_FRAC = 0.88f
    const val CORNER_FRAC = 0.22f

    /** The two cells the mark leaves out. Off the diagonal and off centre on purpose. */
    val EMPTY_CELLS: Set<Cell> = setOf(Cell(1, 2), Cell(2, 0))

    /**
     * The cells that are drawn, in reading order. Built once rather than per frame: the gauge
     * animates for as long as a session is open.
     */
    val FILLED_CELLS: List<Cell> = (0 until CELLS_A_SIDE).flatMap { row ->
        (0 until CELLS_A_SIDE).map { col -> Cell(row, col) }
    }.filterNot { it in EMPTY_CELLS }

    /** Derived, never restated: a hand-kept count is free to disagree with the set it counts. */
    val FILLED_COUNT: Int = FILLED_CELLS.size

    // --- the settle ----------------------------------------------------------------------------

    /** Milliseconds one cell takes to arrive, and the offset between starts. The splash restates both. */
    const val CELL_MS = 260
    const val STAGGER_MS = 70

    /** The smallest a cell is drawn while arriving, as a fraction of its settled side. */
    const val MIN_SCALE = 0.6f

    /** The settle's end: the last cell's start offset plus its own duration, in milliseconds. */
    val SETTLED_MS: Float = ((FILLED_COUNT - 1) * STAGGER_MS + CELL_MS).toFloat()

    /**
     * How far the cell at [index] has arrived when the settle is [elapsed] milliseconds in.
     *
     * One cursor over a fixed schedule, so no two cells can disagree about where in the sequence
     * they are.
     */
    fun cellProgress(index: Int, elapsed: Float): Float = ((elapsed - index * STAGGER_MS) / CELL_MS).coerceIn(0f, 1f)

    // --- the gauge -----------------------------------------------------------------------------

    /**
     * One pass of the wave over the seven cells; any one cell peaks once per pass.
     *
     * Calm on purpose, and with a ceiling as well as a floor: past roughly five seconds the cells
     * drift far enough apart in phase to read as cells fading independently rather than one wave.
     */
    const val WAVE_MS = 3_000

    /**
     * How narrow the crest is. A plain raised cosine (1.0) spreads the bright band over about three
     * cells; raising it contracts the band so the pass reads as a pulse with a front and a tail. It
     * changes the curve every cell traces, never which cell traces which. A sharper crest is a more
     * insistent motion, so it and [WAVE_MS] must not both be pushed at once.
     */
    const val CREST_POWER = 2.0

    private const val TWO_PI = 2.0 * PI

    /**
     * How bright the cell at [index] is when the wave is [phase] of the way through a pass, on 0..1
     * from the track colour to the full one.
     *
     * A raised cosine offset by the cell's share of the lattice, so each cell peaks a beat after the
     * one before it and every cell traces the identical curve. **That every cell is identical is the
     * property worth keeping**: cells differing in height or dwell would rank them, and no cell's
     * place in the pass may mean anything.
     */
    fun waveLevel(index: Int, phase: Float): Float {
        val offset = phase - index.toFloat() / FILLED_COUNT
        val cellPhase = offset - floor(offset)
        val raisedCosine = (cos(TWO_PI * cellPhase) + 1.0) / 2.0
        return raisedCosine.pow(CREST_POWER).toFloat()
    }
}
