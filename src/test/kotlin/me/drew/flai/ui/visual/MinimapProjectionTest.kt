package me.drew.flai.ui.visual

import java.awt.geom.Rectangle2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimapProjectionTest {

    private val content = Rectangle2D.Double(0.0, 0.0, 800.0, 300.0)

    @Test
    fun `viewport larger than content still fits inside minimap`() {
        // Canvas 1200x800 at zoom 1 -> viewport 1200x800 in model coords
        val viewport = Rectangle2D.Double(-200.0, -250.0, 1200.0, 800.0)
        val projection = computeMinimapProjection(content, viewport, 160, 100, 6.0)

        val vx = projection.toMiniX(viewport.x)
        val vy = projection.toMiniY(viewport.y)
        val vx2 = projection.toMiniX(viewport.x + viewport.width)
        val vy2 = projection.toMiniY(viewport.y + viewport.height)

        assertTrue("viewport left inside minimap", vx >= 0.0)
        assertTrue("viewport top inside minimap", vy >= 0.0)
        assertTrue("viewport right inside minimap", vx2 <= 160.0)
        assertTrue("viewport bottom inside minimap", vy2 <= 100.0)
    }

    @Test
    fun `zooming in shrinks projected viewport`() {
        val zoomedOut = Rectangle2D.Double(0.0, 0.0, 1200.0, 800.0)
        val zoomedIn = Rectangle2D.Double(300.0, 200.0, 600.0, 400.0)

        val outProjection = computeMinimapProjection(content, zoomedOut, 160, 100, 6.0)
        val inProjection = computeMinimapProjection(content, zoomedIn, 160, 100, 6.0)

        val outWidth = outProjection.toMiniX(zoomedOut.x + zoomedOut.width) - outProjection.toMiniX(zoomedOut.x)
        val inWidth = inProjection.toMiniX(zoomedIn.x + zoomedIn.width) - inProjection.toMiniX(zoomedIn.x)

        assertTrue("zoomed-in viewport must project smaller than zoomed-out", inWidth < outWidth)
    }

    @Test
    fun `viewport inside content does not change content scale`() {
        val viewport = Rectangle2D.Double(100.0, 50.0, 400.0, 150.0)
        val withViewport = computeMinimapProjection(content, viewport, 160, 100, 6.0)
        val withoutViewport = computeMinimapProjection(content, null, 160, 100, 6.0)

        assertEquals(withoutViewport.scale, withViewport.scale, 1e-9)
    }

    @Test
    fun `content only projection centers content`() {
        val projection = computeMinimapProjection(content, null, 160, 100, 6.0)

        val left = projection.toMiniX(0.0)
        val right = projection.toMiniX(800.0)
        assertEquals("horizontally centered", 160.0 - right, left, 1e-9)

        val top = projection.toMiniY(0.0)
        val bottom = projection.toMiniY(300.0)
        assertEquals("vertically centered", 100.0 - bottom, top, 1e-9)
    }

    @Test
    fun `degenerate content uses minimum extent`() {
        val point = Rectangle2D.Double(50.0, 50.0, 0.0, 0.0)
        val projection = computeMinimapProjection(point, null, 160, 100, 6.0)
        assertTrue(projection.scale.isFinite())
        assertTrue(projection.scale > 0.0)
    }
}
