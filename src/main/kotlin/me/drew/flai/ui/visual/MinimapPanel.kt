package me.drew.flai.ui.visual

import com.intellij.ui.JBColor
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.BorderFactory
import javax.swing.JPanel

private const val MINIMAP_W = 160
private const val MINIMAP_H = 100
private const val MINIMAP_NODE_W = 140
private const val MINIMAP_NODE_H = 60

internal data class MinimapProjection(
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double,
    val minX: Double,
    val minY: Double,
) {
    fun toMiniX(modelX: Double): Double = offsetX + (modelX - minX) * scale

    fun toMiniY(modelY: Double): Double = offsetY + (modelY - minY) * scale
}

internal fun computeMinimapProjection(
    contentBounds: Rectangle2D,
    viewportBounds: Rectangle2D?,
    width: Int,
    height: Int,
    padding: Double,
): MinimapProjection {
    val union = Rectangle2D.Double()
    union.setRect(contentBounds)
    if (viewportBounds != null) {
        Rectangle2D.union(union, viewportBounds, union)
    }
    val boundsW = union.width.coerceAtLeast(1.0)
    val boundsH = union.height.coerceAtLeast(1.0)
    val scaleX = (width - padding * 2) / boundsW
    val scaleY = (height - padding * 2) / boundsH
    val scale = minOf(scaleX, scaleY)
    val offsetX = padding + (width - padding * 2 - boundsW * scale) / 2.0
    val offsetY = padding + (height - padding * 2 - boundsH * scale) / 2.0
    return MinimapProjection(scale, offsetX, offsetY, union.x, union.y)
}

class MinimapPanel(
    private val model: VisualPipelineModel,
    private val getViewTransform: () -> AffineTransform,
    private val getCanvasSize: () -> Dimension,
) : JPanel() {

    init {
        preferredSize = Dimension(MINIMAP_W, MINIMAP_H)
        minimumSize = Dimension(MINIMAP_W, MINIMAP_H)
        maximumSize = Dimension(MINIMAP_W, MINIMAP_H)
        isOpaque = true
        border = BorderFactory.createLineBorder(JBColor(Color(160, 160, 160), Color(80, 80, 80)), 1)
    }

    fun refresh() {
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Background
        g2.color = JBColor(Color(240, 240, 240), Color(40, 40, 40))
        g2.fillRect(0, 0, width, height)

        if (model.nodes.isEmpty()) {
            g2.color = JBColor(Color(160, 160, 160), Color(110, 110, 110))
            val fm = g2.fontMetrics
            val msg = "Empty"
            g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2 + fm.ascent / 2)
            return
        }

        // Compute model bounds
        val minX = model.nodes.minOf { it.x }.toDouble()
        val minY = model.nodes.minOf { it.y }.toDouble()
        val maxX = model.nodes.maxOf { it.x + MINIMAP_NODE_W }.toDouble()
        val maxY = model.nodes.maxOf { it.y + MINIMAP_NODE_H }.toDouble()
        val contentBounds = Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)

        // Viewport in model coords — include it in bounds so the indicator always fits
        val viewportBounds = computeViewportModelBounds()
        val projection = computeMinimapProjection(contentBounds, viewportBounds, width, height, 6.0)

        fun toMiniX(modelX: Double) = projection.toMiniX(modelX).toFloat()
        fun toMiniY(modelY: Double) = projection.toMiniY(modelY).toFloat()
        val scale = projection.scale

        // Draw edges
        g2.stroke = BasicStroke(0.8f)
        g2.color = JBColor(Color(130, 130, 130), Color(120, 120, 120))
        for (edge in model.edges) {
            val fromNode = model.nodeBySeq(edge.fromSeq) ?: continue
            val toNode = model.nodeBySeq(edge.toSeq) ?: continue
            val fx = toMiniX(fromNode.x + MINIMAP_NODE_W.toDouble())
            val fy = toMiniY(fromNode.y + MINIMAP_NODE_H / 2.0)
            val tx = toMiniX(toNode.x.toDouble())
            val ty = toMiniY(toNode.y + MINIMAP_NODE_H / 2.0)
            g2.drawLine(fx.toInt(), fy.toInt(), tx.toInt(), ty.toInt())
        }
        g2.stroke = BasicStroke(1f)

        // Draw node rectangles
        for (node in model.nodes) {
            val rx = toMiniX(node.x.toDouble())
            val ry = toMiniY(node.y.toDouble())
            val rw = (MINIMAP_NODE_W * scale).toFloat().coerceAtLeast(2f)
            val rh = (MINIMAP_NODE_H * scale).toFloat().coerceAtLeast(2f)
            g2.color = FlaiEditorTheme.accentFor(node.gate)
            g2.fillRoundRect(rx.toInt(), ry.toInt(), rw.toInt(), rh.toInt(), 2, 2)
        }

        // Draw viewport indicator
        if (viewportBounds != null) {
            val vx = toMiniX(viewportBounds.x)
            val vy = toMiniY(viewportBounds.y)
            val vw = toMiniX(viewportBounds.x + viewportBounds.width) - vx
            val vh = toMiniY(viewportBounds.y + viewportBounds.height) - vy
            g2.color = JBColor(Color(55, 120, 255, 50), Color(90, 150, 255, 50))
            g2.fill(Rectangle2D.Float(vx, vy, vw, vh))
            g2.color = JBColor(Color(55, 120, 255, 160), Color(90, 150, 255, 160))
            g2.stroke = BasicStroke(1f)
            g2.draw(Rectangle2D.Float(vx, vy, vw, vh))
        }
    }

    private fun computeViewportModelBounds(): Rectangle2D.Double? {
        val canvasSize = getCanvasSize()
        if (canvasSize.width <= 0 || canvasSize.height <= 0) {
            return null
        }
        return try {
            val inv = getViewTransform().createInverse()
            val topLeft = Point2D.Double()
            val bottomRight = Point2D.Double()
            inv.transform(Point2D.Double(0.0, 0.0), topLeft)
            inv.transform(Point2D.Double(canvasSize.width.toDouble(), canvasSize.height.toDouble()), bottomRight)
            Rectangle2D.Double(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
        } catch (_: java.awt.geom.NoninvertibleTransformException) {
            null
        }
    }
}
