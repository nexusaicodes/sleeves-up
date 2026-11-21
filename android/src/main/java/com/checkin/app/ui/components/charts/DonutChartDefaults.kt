package com.checkin.app.ui.components.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizing shared by Reports' three [DonutChart]s — the days split, the start-time split and the
 * sessions-per-day split.
 *
 * Separate from the call site so the stroke and the diameter are stated once beside the reasoning
 * that fixes them, rather than as two bare literals inside a Composable.
 */
object DonutChartDefaults {

    /**
     * Ring thickness. Kept near a tenth of the diameter: the arc has to read as a band rather than a
     * hairline, but every dp of it comes straight out of the hole a caption would sit in.
     */
    val StrokeWidth: Dp = 12.dp

    /**
     * The smallest ring whose hole holds the widest figure a caption would carry: a duration in
     * caption, which is 57.0dp at most ("12 س 59 د" in Plex Arabic). The hole's inscribed square is
     * (diameter - 2 × [StrokeWidth]) × [ChartGeometry.INSCRIBED_SQUARE], so it needs a diameter of at
     * least 24 + 57.0 / 0.707 = 104.6dp; 112dp leaves a 62.2dp square.
     */
    private val BaseSize = 112.dp

    /**
     * The largest ring that leaves Reports' legend whole beside it at the default font scale on a
     * 360dp phone. The card's content is 288dp wide (20dp screen and 16dp card padding each side); the
     * row spends 20dp between ring and legend, and the widest legend line is 103dp of text ("أيام دون
     * حضور" and a three-digit count, in Plex Arabic body2) plus 24dp of swatch and gaps. That leaves
     * 141dp, rounded down to 140dp.
     */
    private val MaxSize = 140.dp

    /**
     * Ring diameter, grown with the user's font scale.
     *
     * Sized for a caption in the hole, which no current caller draws (see
     * [ChartGeometry.INSCRIBED_SQUARE]). That caption would be in `sp` while the ring is a fixed `dp`,
     * so raising the system font size shrinks the hole in real terms until the text no longer fits.
     * Growing the ring in step keeps it whole up to the cap, reached at a font scale of 1.25. Past it a
     * caption ellipsizes, and the legend, which grows too, wraps its labels rather than lose a count
     * (see [com.checkin.app.ui.components.LegendRow]).
     */
    @Composable
    @ReadOnlyComposable
    fun size(): Dp = (BaseSize * LocalDensity.current.fontScale).coerceIn(BaseSize, MaxSize)
}
