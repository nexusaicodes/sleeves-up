package com.checkin.app.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.LayoutDirection
import com.adevinta.spark.components.icons.rememberSparkIconPainter
import com.adevinta.spark.icons.SparkIcon

/**
 * This icon, drawn flipped left to right whenever the layout it lands in is right-to-left.
 *
 * Spark's icons are vector drawables with no `android:autoMirrored`, so a directional one — back, a
 * chevron, a walking figure — points the same way under Arabic as under English, which reads
 * as the opposite direction. This does for them what `Icons.AutoMirrored` does for Material's. It
 * works for any [SparkIcon] in any Spark component, because every one of them draws its icon through
 * [rememberSparkIconPainter], which this wraps.
 *
 * **Directional icons only**: a flipped clock, camera or lock is a wrong picture, not a mirrored one.
 * An icon that already mirrors itself (a [SparkIcon.Vector] built with `autoMirror`) is returned
 * unchanged, since wrapping it would flip it twice and point it back the wrong way.
 *
 * Call it once per icon, at a top-level `val`, not inside a composable: each call returns a new
 * [SparkIcon.AnimatedPainter], and an icon that is never equal to its previous self restarts the
 * painter on every recomposition.
 */
fun SparkIcon.mirroredInRtl(): SparkIcon {
    if (this is SparkIcon.Vector && imageVector.autoMirror) return this
    val icon = this
    return SparkIcon.AnimatedPainter { atEnd ->
        val painter = rememberSparkIconPainter(icon, atEnd)
        remember(painter) { RtlMirroredPainter(painter) }
    }
}

/**
 * Draws [painter] as it is under a left-to-right layout and flipped about its vertical centre line
 * under a right-to-left one.
 *
 * Alpha and colour filter are taken here and handed to [painter] on each draw (returning true from
 * [applyAlpha] and [applyColorFilter]), so a tinted icon keeps its tint and no offscreen layer is
 * allocated for it.
 */
internal class RtlMirroredPainter(private val painter: Painter) : Painter() {
    private var alpha = 1f
    private var colorFilter: ColorFilter? = null

    override val intrinsicSize: Size get() = painter.intrinsicSize

    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }

    override fun DrawScope.onDraw() {
        if (layoutDirection == LayoutDirection.Rtl) {
            scale(scaleX = -1f, scaleY = 1f) { drawWrapped() }
        } else {
            drawWrapped()
        }
    }

    private fun DrawScope.drawWrapped() = with(painter) { draw(size, alpha, colorFilter) }
}
