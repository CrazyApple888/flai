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
        val contentW = (maxX - minX).coerceAtLeast(1.0)
        val contentH = (maxY - minY).coerceAtLeast(1.0)

        val padding = 6.0
        val scaleX = (width - padding * 2) / contentW
        val scaleY = (height - padding * 2) / contentH
        val scale = minOf(scaleX, scaleY)

        val offsetX = padding + (width - padding * 2 - contentW * scale) / 2.0
        val offsetY = padding + (height - padding * 2 - contentH * scale) / 2.0

        fun toMiniX(modelX: Double) = (offsetX + (modelX - minX) * scale).toFloat()
        fun toMiniY(modelY: Double) = (offsetY + (modelY - minY) * scale).toFloat()

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
        val viewTransform = getViewTransform()
        val canvasSize = getCanvasSize()
        try {
            val inv = viewTransform.createInverse()
            val topLeft = Point2D.Double()
            val bottomRight = Point2D.Double()
            inv.transform(Point2D.Double(0.0, 0.0), topLeft)
            inv.transform(Point2D.Double(canvasSize.width.toDouble(), canvasSize.height.toDouble()), bottomRight)
            val vx = toMiniX(topLeft.x)
            val vy = toMiniY(topLeft.y)
            val vw = toMiniX(bottomRight.x) - vx
            val vh = toMiniY(bottomRight.y) - vy
            g2.color = JBColor(Color(55, 120, 255, 50), Color(90, 150, 255, 50))
            g2.fill(Rectangle2D.Float(vx, vy, vw, vh))
            g2.color = JBColor(Color(55, 120, 255, 160), Color(90, 150, 255, 160))
            g2.stroke = BasicStroke(1f)
            g2.draw(Rectangle2D.Float(vx, vy, vw, vh))
        } catch (_: java.awt.geom.NoninvertibleTransformException) {
            // Skip viewport indicator if transform is not invertible
        }
    }
}
