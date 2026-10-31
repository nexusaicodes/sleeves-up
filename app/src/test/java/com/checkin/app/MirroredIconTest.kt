package com.checkin.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.adevinta.spark.icons.SparkIcon
import com.checkin.app.ui.components.RtlMirroredPainter
import com.checkin.app.ui.components.mirroredInRtl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MirroredIconTest {

    /** Records what the wrapper hands on: the size it drew at and the alpha it passed. */
    private class RecordingPainter : Painter() {
        var drawnSize: Size? = null
        var drawnAlpha: Float? = null

        override val intrinsicSize = Size(24f, 24f)

        override fun applyAlpha(alpha: Float): Boolean {
            drawnAlpha = alpha
            return true
        }

        override fun applyColorFilter(colorFilter: ColorFilter?): Boolean = true

        override fun DrawScope.onDraw() {
            drawnSize = size
        }
    }

    /** A canvas that records every scale applied to it and ignores everything else. */
    private fun recordingCanvas(scales: MutableList<Pair<Float, Float>>): Canvas =
        Proxy.newProxyInstance(Canvas::class.java.classLoader, arrayOf(Canvas::class.java)) { _, method, args ->
            if (method.name == "scale") scales += (args[0] as Float) to (args[1] as Float)
            null
        } as Canvas

    private fun drawUnder(direction: LayoutDirection, inner: Painter, alpha: Float = 1f): List<Pair<Float, Float>> {
        val scales = mutableListOf<Pair<Float, Float>>()
        CanvasDrawScope().draw(Density(1f), direction, recordingCanvas(scales), Size(48f, 48f)) {
            with(RtlMirroredPainter(inner)) { draw(size, alpha) }
        }
        return scales
    }

    @Test
    fun `left to right draws the icon as it is`() {
        val inner = RecordingPainter()
        assertTrue(drawUnder(LayoutDirection.Ltr, inner).isEmpty())
        assertEquals(Size(48f, 48f), inner.drawnSize)
    }

    @Test
    fun `right to left flips the icon horizontally and only horizontally`() {
        val inner = RecordingPainter()
        assertEquals(listOf(-1f to 1f), drawUnder(LayoutDirection.Rtl, inner))
        assertEquals(Size(48f, 48f), inner.drawnSize)
    }

    @Test
    fun `alpha reaches the wrapped icon under either direction`() {
        for (direction in LayoutDirection.entries) {
            val inner = RecordingPainter()
            drawUnder(direction, inner, alpha = 0.4f)
            assertEquals(direction.name, 0.4f, inner.drawnAlpha)
        }
    }

    private fun vector(autoMirror: Boolean) = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror,
    ).build()

    @Test
    fun `an icon that already mirrors itself is not wrapped again`() {
        val icon = SparkIcon.Vector(vector(autoMirror = true))
        assertSame(icon, icon.mirroredInRtl())
    }

    @Test
    fun `an icon that does not mirror itself is wrapped`() {
        assertTrue(SparkIcon.Vector(vector(autoMirror = false)).mirroredInRtl() is SparkIcon.AnimatedPainter)
        assertTrue(SparkIcon.DrawableRes(drawableId = 1).mirroredInRtl() is SparkIcon.AnimatedPainter)
    }

    @Test
    fun `intrinsic size is the wrapped icon's`() {
        assertEquals(Size(24f, 24f), RtlMirroredPainter(RecordingPainter()).intrinsicSize)
    }
}
