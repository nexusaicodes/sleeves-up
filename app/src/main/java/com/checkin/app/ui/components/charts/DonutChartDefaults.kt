package com.checkin.app.ui.components.charts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sizing for the all-time-split donut on Reports, the app's one [DonutChart].
 *
 * Separate from the call site so the stroke and the diameter are stated once beside the reasoning
 * that fixes them, rather than as two bare literals inside a Composable.
 */
object DonutChartDefaults {

    /**
     * Ring thickness. Kept near a tenth of the diameter: the arc has to read as a band rather than a
     * hairline, but every dp of it comes straight out of the hole the caption sits in, and the app's
     * own progress gauge runs about half this ratio.
     */
    val StrokeWidth: Dp = 12.dp

    private val BaseSize = 112.dp
    private val MaxSize = 148.dp

    /**
     * Ring diameter, grown with the user's font scale.
     *
     * The caption inside is in `sp` and the ring would otherwise be a fixed `dp`, so raising the
     * system font size shrinks the hole in real terms until the text no longer fits it. Growing the
     * ring in step keeps the caption whole for the scales people actually use; the cap stops it
     * crowding the legend beside it on a narrow screen, and past that the caption ellipsizes.
     */
    @Composable
    @ReadOnlyComposable
    fun size(): Dp = (BaseSize * LocalDensity.current.fontScale).coerceIn(BaseSize, MaxSize)
}
