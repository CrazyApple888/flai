package me.drew.flai.ui.visual

import com.intellij.ui.JBColor
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.model.LogicGate
import me.drew.flai.ui.model.GateStatus
import java.awt.*
import java.awt.geom.GeneralPath
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

data class CanvasRenderState(
    val selectedNodeSeq: Int,
    val hoveredNodeSeq: Int,
    val nodeHoverAlpha: Map<Int, Float>,
    val executionStatus: Map<String, GateStatus>,
    val isGhostDragging: Boolean,
    val ghostNodeSeq: Int,
    val ghostX: Int,
    val ghostY: Int,
    val isDraggingEdge: Boolean,
    val edgeDragFromSeq: Int,
    val edgeDragFromPort: String,
    val edgeDragModelX: Double,
    val edgeDragModelY: Double,
    val animTick: Int,
    val selectedEdge: VisualEdge?,
)

internal class CanvasRenderer {

    fun paint(g2: Graphics2D, model: VisualPipelineModel, state: CanvasRenderState) {
        for (edge in model.edges) {
            drawEdge(g2, edge, model, state)
        }
        for (node in model.nodes) {
            drawNode(g2, node, model, state)
        }
        if (state.isGhostDragging && state.ghostNodeSeq != -1) {
            val node = model.nodeBySeq(state.ghostNodeSeq)
            if (node != null) {
                val oldComposite = g2.composite
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
                val ghostShape = RoundRectangle2D.Float(
                    state.ghostX.toFloat(),
                    state.ghostY.toFloat(),
                    NODE_WIDTH.toFloat(),
                    NODE_HEIGHT.toFloat(),
                    ARC.toFloat(),
                    ARC.toFloat()
                )
                g2.color = FlaiEditorTheme.accentFor(node.gate)
                g2.fill(ghostShape)
                g2.color = FlaiEditorTheme.SELECTION_OUTLINE
                g2.stroke = BasicStroke(2f)
                g2.draw(ghostShape)
                g2.stroke = BasicStroke(1f)
                g2.composite = oldComposite
            }
        }
        if (state.isDraggingEdge && state.edgeDragFromSeq != -1) {
            val fromNode = model.nodeBySeq(state.edgeDragFromSeq)
            if (fromNode != null) {
                val portCenter = outputPortCenter(fromNode, state.edgeDragFromPort)
                g2.color = JBColor(Color(0, 0, 0, 128), Color(200, 200, 200, 128))
                g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(6f, 4f), 0f)
                drawBezier(g2, portCenter.x, portCenter.y, state.edgeDragModelX, state.edgeDragModelY)
                g2.stroke = BasicStroke(1f)
            }
        }
    }

    private fun drawNode(g2: Graphics2D, node: VisualNode, model: VisualPipelineModel, state: CanvasRenderState) {
        val x = node.x
        val y = node.y
        val isSelected = node.nodeSeq == state.selectedNodeSeq
        val isHovered = node.nodeSeq == state.hoveredNodeSeq
        val isEntry = node.nodeSeq == model.entryNodeSeq
        val accent = FlaiEditorTheme.accentFor(node.gate)

        val shape = RoundRectangle2D.Float(
            x.toFloat(),
            y.toFloat(),
            NODE_WIDTH.toFloat(),
            NODE_HEIGHT.toFloat(),
            ARC.toFloat(),
            ARC.toFloat()
        )

        val shadowOffset = if (isHovered) 5 else 3
        g2.color = FlaiEditorTheme.NODE_SHADOW
        val shadowShape = RoundRectangle2D.Float(
            (x + shadowOffset).toFloat(),
            (y + shadowOffset).toFloat(),
            NODE_WIDTH.toFloat(),
            NODE_HEIGHT.toFloat(),
            ARC.toFloat(),
            ARC.toFloat()
        )
        g2.fill(shadowShape)

        if (isSelected) {
            g2.color = FlaiEditorTheme.NODE_SELECTED_GLOW
            val glowSize = 6f
            val glowShape = RoundRectangle2D.Float(
                x - glowSize,
                y - glowSize,
                NODE_WIDTH + glowSize * 2,
                NODE_HEIGHT + glowSize * 2,
                ARC + glowSize,
                ARC + glowSize
            )
            g2.fill(glowShape)
        }

        g2.color = FlaiEditorTheme.NODE_BG
        g2.fill(shape)

        val clip = g2.clip
        g2.clip = Rectangle(x, y, 4, NODE_HEIGHT)
        g2.color = accent
        g2.fillRoundRect(x, y, 4, NODE_HEIGHT, ARC, ARC)
        g2.clip = clip

        g2.stroke = if (isSelected) BasicStroke(2.5f) else BasicStroke(1f)
        g2.color = if (isSelected) {
            FlaiEditorTheme.SELECTION_OUTLINE
        } else {
            val brightened = accent.brighter()
            if (isHovered) brightened else JBColor(Color(180, 180, 180), Color(80, 80, 80))
        }
        g2.draw(shape)
        g2.stroke = BasicStroke(1f)

        if (isEntry) {
            val triX = intArrayOf(x + 2, x + 10, x + 2)
            val triY = intArrayOf(y + 2, y + 2, y + 10)
            g2.color = JBColor(Color(200, 100, 0), Color(255, 170, 40))
            g2.fillPolygon(triX, triY, 3)
        }

        val icon = gateIcon(node.gate)
        val iconX = x + 8
        val iconY = y + (NODE_HEIGHT - icon.iconHeight) / 2
        icon.paintIcon(null, g2, iconX, iconY)

        g2.color = JBColor(Color(30, 30, 30), Color(230, 230, 230))
        val fm = g2.fontMetrics
        val label = node.gate.label
        val textStartX = iconX + icon.iconWidth + 6
        val availWidth = NODE_WIDTH - textStartX + x - 6
        val displayLabel = if (fm.stringWidth(label) > availWidth) {
            var truncated = label
            while (truncated.isNotEmpty() && fm.stringWidth("$truncated…") > availWidth) {
                truncated = truncated.dropLast(1)
            }
            "$truncated…"
        } else {
            label
        }
        val textY = y + (NODE_HEIGHT + fm.ascent - fm.descent) / 2
        g2.drawString(displayLabel, textStartX, textY)

        if (node.gate is LlmGate) {
            drawLlmStar(g2, x + NODE_WIDTH - 10, y + 10, 6)
        }

        val status = state.executionStatus[node.gate.label]
        if (status != null) {
            drawStatusBadge(g2, x + NODE_WIDTH - 16, y + 4, status, state.animTick)
        }

        val isNodeActive = isSelected || isHovered
        val inputPorts = node.gate.inputPorts()
        for ((i, _) in inputPorts.withIndex()) {
            val portY = y + NODE_HEIGHT / 2 + i * (PORT_RADIUS * 2 + 2) - (inputPorts.size - 1) * (PORT_RADIUS + 1)
            drawPort(g2, x - PORT_RADIUS, portY, false, isNodeActive)
        }

        val outputPorts = node.gate.outputPorts()
        val outCount = outputPorts.size
        for ((i, portName) in outputPorts.withIndex()) {
            val portY = outputPortY(y, i, outCount)
            val portColor = logicBranchColor(node.gate, portName)
            val portLabel = logicPortLabel(node.gate, portName)
            drawPort(g2, x + NODE_WIDTH, portY, true, isNodeActive, portColor, portLabel)
        }
    }

    private fun drawLlmStar(g2: Graphics2D, cx: Int, cy: Int, r: Int) {
        val pts = 5
        val innerR = r / 2.5
        val xPts = IntArray(pts * 2)
        val yPts = IntArray(pts * 2)
        for (i in 0 until pts * 2) {
            val angle = Math.PI / pts * i - Math.PI / 2
            val radius: Double = if (i % 2 == 0) r.toDouble() else innerR
            xPts[i] = (cx + radius * Math.cos(angle)).toInt()
            yPts[i] = (cy + radius * Math.sin(angle)).toInt()
        }
        g2.color = JBColor(Color(220, 180, 30), Color(255, 210, 60))
        g2.fillPolygon(xPts, yPts, pts * 2)
    }

    private fun drawPort(
        g2: Graphics2D,
        cx: Int,
        cy: Int,
        isOutput: Boolean,
        isNodeActive: Boolean = false,
        color: Color? = null,
        label: String? = null
    ) {
        val r = PORT_RADIUS
        val portColor = color ?: if (isOutput) {
            FlaiEditorTheme.PORT_OUTPUT
        } else {
            FlaiEditorTheme.PORT_INPUT
        }
        if (isNodeActive) {
            g2.color = Color(portColor.red, portColor.green, portColor.blue, 80)
            val glowR = r + 3
            g2.fillOval(cx - glowR, cy - glowR, glowR * 2, glowR * 2)
        }
        g2.color = portColor
        g2.fillOval(cx - r, cy - r, r * 2, r * 2)
        g2.color = JBColor(Color(60, 60, 60), Color(180, 180, 180))
        g2.stroke = BasicStroke(1f)
        g2.drawOval(cx - r, cy - r, r * 2, r * 2)
        if (label != null) {
            val savedFont = g2.font
            g2.font = Font(Font.SANS_SERIF, Font.BOLD, 7)
            val fm = g2.fontMetrics
            g2.color = Color.WHITE
            g2.drawString(label, cx - fm.stringWidth(label) / 2, cy + fm.ascent / 2 - 1)
            g2.font = savedFont
        }
    }

    private fun drawStatusBadge(g2: Graphics2D, cx: Int, cy: Int, status: GateStatus, animTick: Int) {
        val r = 7
        when (status) {
            GateStatus.RUNNING -> {
                val alpha = 128 + (Math.sin(animTick * 0.4) * 127).toInt()
                g2.color = Color(50, 100, 220, alpha.coerceIn(0, 255))
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            }

            GateStatus.SUCCESS -> {
                g2.color = Color(0, 180, 0)
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            }

            GateStatus.FAILURE -> {
                g2.color = Color(220, 0, 0)
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            }

            GateStatus.TOLERATED_FAILURE -> {
                g2.color = Color(230, 160, 0)
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            }

            GateStatus.OUTPUT -> {
                g2.color = Color(180, 100, 0)
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            }
        }
    }

    private fun drawEdge(g2: Graphics2D, edge: VisualEdge, model: VisualPipelineModel, state: CanvasRenderState) {
        val fromNode = model.nodeBySeq(edge.fromSeq) ?: return
        val toNode = model.nodeBySeq(edge.toSeq) ?: return
        val fromPt = outputPortCenter(fromNode, edge.fromPort)
        val toPt = inputPortCenter(toNode, edge.toPort)
        val isSelected = edge == state.selectedEdge
        val isActive = isSelected ||
                fromNode.nodeSeq == state.selectedNodeSeq || toNode.nodeSeq == state.selectedNodeSeq ||
                fromNode.nodeSeq == state.hoveredNodeSeq || toNode.nodeSeq == state.hoveredNodeSeq
        val branchColor = logicBranchColor(fromNode.gate, edge.fromPort)
        g2.stroke = BasicStroke(
            if (isSelected) {
                2.5f
            } else {
                1.5f
            },
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
        )
        g2.color = when {
            isSelected -> JBColor(Color(0, 100, 220), Color(100, 160, 255))
            branchColor != null -> {
                val alpha = if (isActive) 220 else 160
                Color(branchColor.red, branchColor.green, branchColor.blue, alpha)
            }

            isActive -> JBColor(Color(110, 130, 200), Color(160, 180, 240))
            else -> JBColor(Color(80, 80, 80), Color(150, 150, 150))
        }
        drawBezier(g2, fromPt.x, fromPt.y, toPt.x, toPt.y)
        g2.stroke = BasicStroke(1f)
    }

    internal fun drawBezier(g2: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double) {
        val ctrlOffset = (Math.abs(x2 - x1) / 2).coerceAtLeast(60.0)
        val path = GeneralPath()
        path.moveTo(x1, y1)
        path.curveTo(x1 + ctrlOffset, y1, x2 - ctrlOffset, y2, x2, y2)
        g2.draw(path)
    }

    internal fun outputPortCenter(node: VisualNode, port: String): Point2D.Double {
        val ports = node.gate.outputPorts()
        val i = ports.indexOf(port).coerceAtLeast(0)
        val cy = outputPortY(node.y, i, ports.size)
        return Point2D.Double((node.x + NODE_WIDTH).toDouble(), cy.toDouble())
    }

    internal fun inputPortCenter(node: VisualNode, port: String): Point2D.Double {
        val ports = node.gate.inputPorts()
        val i = ports.indexOf(port).coerceAtLeast(0)
        val cy = node.y + NODE_HEIGHT / 2 + i * (PORT_RADIUS * 2 + 2) - (ports.size - 1) * (PORT_RADIUS + 1)
        return Point2D.Double(node.x.toDouble(), cy.toDouble())
    }

    internal fun outputPortY(nodeY: Int, i: Int, outCount: Int): Int {
        val spacing = PORT_RADIUS * 2 + 4
        return nodeY + NODE_HEIGHT / 2 + i * spacing - (outCount - 1) * spacing / 2
    }

    private fun logicBranchColor(gate: Gate, portName: String): Color? {
        if (gate !is LogicGate) {
            return null
        }
        val branchIndex = gate.branches.indexOfFirst { it.port == portName }
        return if (branchIndex >= 0) FlaiEditorTheme.branchColor(branchIndex) else FlaiEditorTheme.BRANCH_DEFAULT_COLOR
    }

    private fun logicPortLabel(gate: Gate, portName: String): String? {
        if (gate !is LogicGate) {
            return null
        }
        val branchIndex = gate.branches.indexOfFirst { it.port == portName }
        return if (branchIndex >= 0) "${branchIndex + 1}" else "D"
    }

    private fun gateIcon(gate: Gate): Icon = FlaiEditorTheme.iconFor(gate)
}
