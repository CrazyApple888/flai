package me.drew.flai.ui.visual

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.ui.JBColor
import me.drew.flai.domain.model.*
import me.drew.flai.ui.editor.FlaiIcons
import me.drew.flai.ui.model.GateStatus
import java.awt.*
import java.awt.event.*
import java.awt.geom.*
import javax.swing.*
import javax.swing.Timer

private const val NODE_WIDTH = 140
private const val NODE_HEIGHT = 60
private const val PORT_RADIUS = 8
private const val ARC = 12
private const val DRAG_THRESHOLD = 2
private const val MIN_ZOOM = 0.3
private const val MAX_ZOOM = 2.5

class PipelineCanvas(private val model: VisualPipelineModel) : JPanel() {

    var isEditable: Boolean = true
    var onNodeSelected: ((VisualNode?) -> Unit) = {}
    var executionStatus: Map<String, GateStatus> = emptyMap()
        set(value) {
            field = value
            updateAnimationTimer()
        }

    var zoomLocked: Boolean = false
    var onLlmStarClicked: ((VisualNode) -> Unit) = {}
    var onRepaint: (() -> Unit) = {}

    private var selectedNodeSeq: Int = -1
    private var selectedEdge: VisualEdge? = null
    private val transform = AffineTransform()
    private var zoom = 1.0

    // Drag state (ghost drag)
    private var dragNodeSeq: Int = -1
    private var dragStartX: Int = 0
    private var dragStartY: Int = 0
    private var isGhostDragging: Boolean = false
    private var ghostDragOffsetX: Int = 0
    private var ghostDragOffsetY: Int = 0
    private var originalNodeX: Int = 0
    private var originalNodeY: Int = 0
    private var isPanning: Boolean = false
    private var panStartX: Int = 0
    private var panStartY: Int = 0

    // Edge drag state
    private var edgeDragFromSeq: Int = -1
    private var edgeDragFromPort: String = ""
    private var edgeDragCurrentX: Int = 0
    private var edgeDragCurrentY: Int = 0
    private var isDraggingEdge: Boolean = false

    // Hover state
    private var hoveredNodeSeq: Int = -1
    private val nodeHoverAlpha: MutableMap<Int, Float> = mutableMapOf()
    private val hoverTimer = Timer(60) {
        tickHoverAlpha()
        if (isShowing) repaint()
    }

    // Animation (execution pulse)
    private var animTick: Int = 0
    private val animTimer = Timer(100) {
        animTick++
        repaint()
    }

    init {
        background = FlaiEditorTheme.CANVAS_BG
        isFocusable = true
        ToolTipManager.sharedInstance().registerComponent(this)

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                val modelPt = inversePoint(e.x, e.y)
                val mx = modelPt.x.toInt()
                val my = modelPt.y.toInt()

                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e, mx, my)
                    return
                }

                // Check output port first (for edge dragging)
                if (isEditable) {
                    val portHit = findOutputPortAt(mx, my)
                    if (portHit != null) {
                        edgeDragFromSeq = portHit.first
                        edgeDragFromPort = portHit.second
                        edgeDragCurrentX = e.x
                        edgeDragCurrentY = e.y
                        isDraggingEdge = true
                        repaint()
                        return
                    }
                }

                // Check LLM star hit region first
                val llmStarNode = findLlmStarAt(mx, my)
                if (llmStarNode != null) {
                    if (selectedNodeSeq != llmStarNode.nodeSeq) {
                        selectedNodeSeq = llmStarNode.nodeSeq
                        selectedEdge = null
                        onNodeSelected(llmStarNode)
                    }
                    onLlmStarClicked(llmStarNode)
                    repaint()
                    return
                }

                // Check node
                val node = findNodeAt(mx, my)
                if (node != null) {
                    selectedNodeSeq = node.nodeSeq
                    selectedEdge = null
                    dragNodeSeq = node.nodeSeq
                    dragStartX = e.x
                    dragStartY = e.y
                    isGhostDragging = false
                    onNodeSelected(node)
                    repaint()
                    return
                }

                // Check edge
                val edge = findEdgeAt(mx, my)
                if (edge != null) {
                    selectedEdge = edge
                    selectedNodeSeq = -1
                    onNodeSelected(null)
                    repaint()
                    return
                }

                // Pan
                selectedNodeSeq = -1
                selectedEdge = null
                onNodeSelected(null)
                isPanning = true
                panStartX = e.x
                panStartY = e.y
                repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                if (isDraggingEdge) {
                    edgeDragCurrentX = e.x
                    edgeDragCurrentY = e.y
                    repaint()
                    return
                }

                if (dragNodeSeq != -1 && isEditable) {
                    val dx = e.x - dragStartX
                    val dy = e.y - dragStartY
                    if (!isGhostDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                        isGhostDragging = true
                        val node = model.nodeBySeq(dragNodeSeq) ?: return
                        originalNodeX = node.x
                        originalNodeY = node.y
                        ghostDragOffsetX = 0
                        ghostDragOffsetY = 0
                    }
                    if (isGhostDragging) {
                        val modelStart = inversePoint(dragStartX, dragStartY)
                        val modelCurrent = inversePoint(e.x, e.y)
                        ghostDragOffsetX = (modelCurrent.x - modelStart.x).toInt()
                        ghostDragOffsetY = (modelCurrent.y - modelStart.y).toInt()
                        repaint()
                    }
                    return
                }

                if (isPanning) {
                    val dx = e.x - panStartX
                    val dy = e.y - panStartY
                    transform.preConcatenate(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()))
                    panStartX = e.x
                    panStartY = e.y
                    repaint()
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (isDraggingEdge) {
                    isDraggingEdge = false
                    if (isEditable) {
                        val modelPt = inversePoint(e.x, e.y)
                        val portHit = findInputPortAt(modelPt.x.toInt(), modelPt.y.toInt())
                        if (portHit != null && portHit.first != edgeDragFromSeq) {
                            model.addEdge(
                                VisualEdge(
                                    fromSeq = edgeDragFromSeq,
                                    fromPort = edgeDragFromPort,
                                    toSeq = portHit.first,
                                    toPort = portHit.second,
                                )
                            )
                        }
                    }
                    edgeDragFromSeq = -1
                    edgeDragFromPort = ""
                    repaint()
                    return
                }

                if (isGhostDragging && dragNodeSeq != -1) {
                    val snappedX = snapToGrid(originalNodeX + ghostDragOffsetX, FlaiEditorTheme.GRID_SIZE)
                    val snappedY = snapToGrid(originalNodeY + ghostDragOffsetY, FlaiEditorTheme.GRID_SIZE)
                    model.moveNode(dragNodeSeq, snappedX, snappedY)
                }
                dragNodeSeq = -1
                isGhostDragging = false
                ghostDragOffsetX = 0
                ghostDragOffsetY = 0
                isPanning = false
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val modelPt = inversePoint(e.x, e.y)
                val node = findNodeAt(modelPt.x.toInt(), modelPt.y.toInt())
                val newHovered = node?.nodeSeq ?: -1
                if (newHovered != hoveredNodeSeq) {
                    hoveredNodeSeq = newHovered
                    if (newHovered != -1) {
                        if (!hoverTimer.isRunning) hoverTimer.start()
                    }
                    repaint()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                if (hoveredNodeSeq != -1) {
                    hoveredNodeSeq = -1
                    repaint()
                }
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                if (zoomLocked) return
                val rotation = e.preciseWheelRotation
                if (Math.abs(rotation) < 0.1) return
                val factor = if (rotation < 0) 1.1 else 1.0 / 1.1
                val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (newZoom == zoom) return
                val ratio = newZoom / zoom
                zoom = newZoom
                // Zoom around screen point — preConcatenate in reverse order so pivot = cursor
                val sx = e.x.toDouble()
                val sy = e.y.toDouble()
                val zoomAt = AffineTransform()
                zoomAt.translate(sx, sy)
                zoomAt.scale(ratio, ratio)
                zoomAt.translate(-sx, -sy)
                transform.preConcatenate(zoomAt)
                repaint()
            }

            override fun mouseClicked(e: MouseEvent) {}
        }

        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
        addMouseWheelListener(mouseAdapter)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ESCAPE && isGhostDragging) {
                    isGhostDragging = false
                    ghostDragOffsetX = 0
                    ghostDragOffsetY = 0
                    dragNodeSeq = -1
                    repaint()
                    return
                }
                if (e.keyCode == KeyEvent.VK_DELETE || e.keyCode == KeyEvent.VK_BACK_SPACE) {
                    if (!isEditable) return
                    val edge = selectedEdge
                    if (edge != null) {
                        model.removeEdge(edge)
                        selectedEdge = null
                        onNodeSelected(null)
                        repaint()
                        return
                    }
                    val nodeSeq = selectedNodeSeq
                    if (nodeSeq != -1) {
                        model.removeNode(nodeSeq)
                        selectedNodeSeq = -1
                        onNodeSelected(null)
                        repaint()
                    }
                }
            }
        })

        val undoAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                if (!isEditable) return
                if (model.undo()) {
                    selectedNodeSeq = -1
                    selectedEdge = null
                    onNodeSelected(null)
                    repaint()
                }
            }
        }
        undoAction.registerCustomShortcutSet(
            ActionManager.getInstance().getAction(IdeActions.ACTION_UNDO).shortcutSet,
            this,
        )

        // TransferHandler to accept gate-type drops from palette
        transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                if (!isEditable) return false
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!isEditable) return false
                val gateType = try {
                    support.transferable.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                        ?: return false
                } catch (_: Exception) {
                    return false
                }
                val dropPoint = support.dropLocation.dropPoint
                val modelPt = inversePoint(dropPoint.x, dropPoint.y)
                val gate = createDefaultGate(gateType) ?: return false
                model.addNode(gate, modelPt.x.toInt(), modelPt.y.toInt())
                repaint()
                return true
            }
        }
    }

    private fun createDefaultGate(gateType: String): Gate? {
        val id = GateId(gateType + "_" + System.currentTimeMillis().toString().takeLast(4))
        return when (gateType) {
            "input" -> InputGate(id = id, label = "Input")
            "output" -> OutputGate(id = id, label = "Output")
            "llm" -> LlmGate(
                id = id,
                label = "LLM",
                promptTemplate = "",
                endpointConfig = LlmEndpointConfig(url = "", credentialId = "", model = ""),
            )
            "logic" -> LogicGate(id = id, label = "Logic", branches = emptyList(), defaultPort = "default")
            "tool" -> ToolGate(id = id, label = "Tool", toolName = "")
            "read-file" -> ReadFileGate(id = id, label = "Read File", path = "", outputKey = "content")
            "write-file" -> WriteFileGate(id = id, label = "Write File", path = "", contentKey = "content")
            else -> null
        }
    }

    private fun updateAnimationTimer() {
        val hasRunning = executionStatus.values.any { it == GateStatus.RUNNING }
        if (hasRunning && !animTimer.isRunning) {
            animTimer.start()
        } else if (!hasRunning && animTimer.isRunning) {
            animTimer.stop()
        }
    }

    fun resetTransform() {
        if (model.nodes.isEmpty()) {
            transform.setToIdentity()
            zoom = 1.0
            repaint()
            return
        }
        val minX = model.nodes.minOf { it.x }
        val minY = model.nodes.minOf { it.y }
        val maxX = model.nodes.maxOf { it.x + NODE_WIDTH }
        val maxY = model.nodes.maxOf { it.y + NODE_HEIGHT }
        val contentW = maxX - minX
        val contentH = maxY - minY
        val margin = 40
        val scaleX = if (contentW > 0) (width - margin * 2).toDouble() / contentW else 1.0
        val scaleY = if (contentH > 0) (height - margin * 2).toDouble() / contentH else 1.0
        val scale = minOf(scaleX, scaleY, MAX_ZOOM).coerceAtLeast(MIN_ZOOM)
        zoom = scale
        transform.setToIdentity()
        transform.translate(margin.toDouble(), margin.toDouble())
        transform.scale(scale, scale)
        transform.translate(-minX.toDouble(), -minY.toDouble())
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val savedTransform = g2.transform

        // Draw dot grid (in screen space, before applying model transform)
        drawDotGrid(g2)

        val combined = AffineTransform(savedTransform)
        combined.concatenate(transform)
        g2.transform = combined

        // Draw edges
        for (edge in model.edges) {
            drawEdge(g2, edge)
        }

        // Draw nodes
        for (node in model.nodes) {
            drawNode(g2, node)
        }

        // Draw ghost overlay during node drag
        if (isGhostDragging && dragNodeSeq != -1) {
            val node = model.nodeBySeq(dragNodeSeq)
            if (node != null) {
                val ghostX = originalNodeX + ghostDragOffsetX
                val ghostY = originalNodeY + ghostDragOffsetY
                val snappedX = snapToGrid(ghostX, FlaiEditorTheme.GRID_SIZE)
                val snappedY = snapToGrid(ghostY, FlaiEditorTheme.GRID_SIZE)
                val oldComposite = g2.composite
                g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
                val ghostShape = RoundRectangle2D.Float(snappedX.toFloat(), snappedY.toFloat(), NODE_WIDTH.toFloat(), NODE_HEIGHT.toFloat(), ARC.toFloat(), ARC.toFloat())
                g2.color = FlaiEditorTheme.accentFor(node.gate)
                g2.fill(ghostShape)
                g2.color = FlaiEditorTheme.SELECTION_OUTLINE
                g2.stroke = BasicStroke(2f)
                g2.draw(ghostShape)
                g2.stroke = BasicStroke(1f)
                g2.composite = oldComposite
            }
        }

        // Draw live edge drag preview
        if (isDraggingEdge && edgeDragFromSeq != -1) {
            val fromNode = model.nodeBySeq(edgeDragFromSeq)
            if (fromNode != null) {
                val portCenter = outputPortCenter(fromNode, edgeDragFromPort)
                val endPt = inversePoint(edgeDragCurrentX, edgeDragCurrentY)
                g2.color = JBColor(Color(0, 0, 0, 128), Color(200, 200, 200, 128))
                g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(6f, 4f), 0f)
                drawBezier(g2, portCenter.x, portCenter.y, endPt.x, endPt.y)
                g2.stroke = BasicStroke(1f)
            }
        }

        g2.transform = savedTransform
        onRepaint()
    }

    private fun drawDotGrid(g2: Graphics2D) {
        // Map grid points from model space to screen space using current transform
        // Use screen-space step derived from model GRID_SIZE
        val modelGridSize = FlaiEditorTheme.GRID_SIZE.toDouble()
        val screenStep = (transform.scaleX * modelGridSize).coerceAtLeast(4.0)
        if (screenStep < 4.0) return

        // Find model origin in screen space
        val originScreen = Point2D.Double(0.0, 0.0)
        transform.transform(originScreen, originScreen)

        val dotR = 1
        g2.color = FlaiEditorTheme.GRID_DOT_COLOR

        val startX = (originScreen.x % screenStep).let { if (it < 0) it + screenStep else it }
        val startY = (originScreen.y % screenStep).let { if (it < 0) it + screenStep else it }

        var sx = startX
        while (sx < width) {
            var sy = startY
            while (sy < height) {
                g2.fillOval((sx - dotR).toInt(), (sy - dotR).toInt(), dotR * 2, dotR * 2)
                sy += screenStep
            }
            sx += screenStep
        }
    }

    private fun gateIcon(gate: Gate): javax.swing.Icon = when (gate) {
        is InputGate -> FlaiIcons.GATE_INPUT
        is OutputGate -> FlaiIcons.GATE_OUTPUT
        is LlmGate -> FlaiIcons.GATE_LLM
        is LogicGate -> FlaiIcons.GATE_LOGIC
        is ToolGate -> FlaiIcons.GATE_TOOL
        is ReadFileGate -> FlaiIcons.GATE_READ_FILE
        is WriteFileGate -> FlaiIcons.GATE_WRITE_FILE
    }

    private fun drawNode(g2: Graphics2D, node: VisualNode) {
        val x = node.x
        val y = node.y
        val isSelected = node.nodeSeq == selectedNodeSeq
        val isHovered = node.nodeSeq == hoveredNodeSeq
        val isEntry = node.nodeSeq == model.entryNodeSeq
        val accent = FlaiEditorTheme.accentFor(node.gate)
        val hoverAlpha = nodeHoverAlpha.getOrDefault(node.nodeSeq, 0f)

        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), NODE_WIDTH.toFloat(), NODE_HEIGHT.toFloat(), ARC.toFloat(), ARC.toFloat())

        // Drop shadow — elevated slightly on hover
        val shadowOffset = if (isHovered) 5 else 3
        val shadowColor = FlaiEditorTheme.NODE_SHADOW
        g2.color = shadowColor
        val shadowShape = RoundRectangle2D.Float((x + shadowOffset).toFloat(), (y + shadowOffset).toFloat(), NODE_WIDTH.toFloat(), NODE_HEIGHT.toFloat(), ARC.toFloat(), ARC.toFloat())
        g2.fill(shadowShape)

        // Selection outer glow
        if (isSelected) {
            g2.color = FlaiEditorTheme.NODE_SELECTED_GLOW
            val glowSize = 6f
            val glowShape = RoundRectangle2D.Float(x - glowSize, y - glowSize, NODE_WIDTH + glowSize * 2, NODE_HEIGHT + glowSize * 2, ARC + glowSize, ARC + glowSize)
            g2.fill(glowShape)
        }

        // Card background
        g2.color = FlaiEditorTheme.NODE_BG
        g2.fill(shape)

        // Left accent border (4 px)
        val accentBorder = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), 4f, NODE_HEIGHT.toFloat(), ARC.toFloat(), ARC.toFloat())
        // For the left bar we clip to a rectangle to avoid rounded right edge on the bar
        val clip = g2.clip
        g2.clip = Rectangle(x, y, 4, NODE_HEIGHT)
        g2.color = accent
        g2.fillRoundRect(x, y, 4, NODE_HEIGHT, ARC, ARC)
        g2.clip = clip

        // Border
        g2.stroke = if (isSelected) BasicStroke(2.5f) else BasicStroke(1f)
        g2.color = if (isSelected) {
            FlaiEditorTheme.SELECTION_OUTLINE
        } else {
            val brightened = accent.brighter()
            if (isHovered) brightened else JBColor(Color(180, 180, 180), Color(80, 80, 80))
        }
        g2.draw(shape)
        g2.stroke = BasicStroke(1f)

        // Entry marker: small filled orange triangle at top-left corner
        if (isEntry) {
            val triX = intArrayOf(x + 2, x + 10, x + 2)
            val triY = intArrayOf(y + 2, y + 2, y + 10)
            g2.color = JBColor(Color(200, 100, 0), Color(255, 170, 40))
            g2.fillPolygon(triX, triY, 3)
        }

        // Gate icon (left of label, tinted with accent)
        val icon = gateIcon(node.gate)
        val iconX = x + 8
        val iconY = y + (NODE_HEIGHT - icon.iconHeight) / 2
        // Paint icon; tint by temporarily setting a composite
        icon.paintIcon(null, g2, iconX, iconY)

        // Label text
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

        // LLM star icon (top-right, clickable — step 6)
        if (node.gate is LlmGate) {
            drawLlmStar(g2, x + NODE_WIDTH - 10, y + 10, 6)
        }

        // Execution status badge
        val status = executionStatus[node.gate.label]
        if (status != null) {
            drawStatusBadge(g2, x + NODE_WIDTH - 16, y + 4, status)
        }

        // Input ports (left edge, center)
        val isNodeActive = isSelected || isHovered
        val inputPorts = node.gate.inputPorts()
        for ((i, _) in inputPorts.withIndex()) {
            val portY = y + NODE_HEIGHT / 2 + i * (PORT_RADIUS * 2 + 2) - (inputPorts.size - 1) * (PORT_RADIUS + 1)
            drawPort(g2, x - PORT_RADIUS, portY, false, isNodeActive)
        }

        // Output ports (right edge)
        val outputPorts = node.gate.outputPorts()
        val outCount = outputPorts.size
        for ((i, _) in outputPorts.withIndex()) {
            val portY = y + NODE_HEIGHT / 2 + (i - (outCount - 1) / 2.0).toInt() * (PORT_RADIUS * 2 + 4)
            drawPort(g2, x + NODE_WIDTH, portY, true, isNodeActive)
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

    private fun drawPort(g2: Graphics2D, cx: Int, cy: Int, isOutput: Boolean, isNodeActive: Boolean = false) {
        val r = PORT_RADIUS
        // Glow ring when node is selected/hovered
        if (isNodeActive) {
            g2.color = if (isOutput) Color(60, 190, 90, 80) else Color(110, 130, 190, 80)
            val glowR = r + 3
            g2.fillOval(cx - glowR, cy - glowR, glowR * 2, glowR * 2)
        }
        g2.color = if (isOutput) FlaiEditorTheme.PORT_OUTPUT else FlaiEditorTheme.PORT_INPUT
        g2.fillOval(cx - r, cy - r, r * 2, r * 2)
        g2.color = JBColor(Color(60, 60, 60), Color(180, 180, 180))
        g2.stroke = BasicStroke(1f)
        g2.drawOval(cx - r, cy - r, r * 2, r * 2)
    }

    private fun drawStatusBadge(g2: Graphics2D, cx: Int, cy: Int, status: GateStatus) {
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
            GateStatus.OUTPUT -> {
                g2.color = Color(180, 100, 0)
                g2.fillOval(cx - r, cy - r, r * 2, r * 2)
            }
        }
    }

    private fun drawEdge(g2: Graphics2D, edge: VisualEdge) {
        val fromNode = model.nodeBySeq(edge.fromSeq) ?: return
        val toNode = model.nodeBySeq(edge.toSeq) ?: return
        val fromPt = outputPortCenter(fromNode, edge.fromPort)
        val toPt = inputPortCenter(toNode, edge.toPort)
        val isSelected = edge == selectedEdge
        val isActive = isSelected ||
            fromNode.nodeSeq == selectedNodeSeq || toNode.nodeSeq == selectedNodeSeq ||
            fromNode.nodeSeq == hoveredNodeSeq || toNode.nodeSeq == hoveredNodeSeq
        g2.stroke = BasicStroke(if (isSelected) 2.5f else 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color = when {
            isSelected -> JBColor(Color(0, 100, 220), Color(100, 160, 255))
            isActive -> JBColor(Color(110, 130, 200), Color(160, 180, 240))
            else -> JBColor(Color(80, 80, 80), Color(150, 150, 150))
        }
        drawBezier(g2, fromPt.x, fromPt.y, toPt.x, toPt.y)
        g2.stroke = BasicStroke(1f)
    }

    private fun drawBezier(g2: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double) {
        val ctrlOffset = (Math.abs(x2 - x1) / 2).coerceAtLeast(60.0)
        val path = GeneralPath()
        path.moveTo(x1, y1)
        path.curveTo(x1 + ctrlOffset, y1, x2 - ctrlOffset, y2, x2, y2)
        g2.draw(path)
    }

    // Port center helpers (in model space)
    private fun outputPortCenter(node: VisualNode, port: String): Point2D.Double {
        val ports = node.gate.outputPorts()
        val i = ports.indexOf(port).coerceAtLeast(0)
        val outCount = ports.size
        val cy = node.y + NODE_HEIGHT / 2 + (i - (outCount - 1) / 2.0).toInt() * (PORT_RADIUS * 2 + 4)
        return Point2D.Double((node.x + NODE_WIDTH).toDouble(), cy.toDouble())
    }

    private fun inputPortCenter(node: VisualNode, port: String): Point2D.Double {
        val ports = node.gate.inputPorts()
        val i = ports.indexOf(port).coerceAtLeast(0)
        val cy = node.y + NODE_HEIGHT / 2 + i * (PORT_RADIUS * 2 + 2) - (ports.size - 1) * (PORT_RADIUS + 1)
        return Point2D.Double(node.x.toDouble(), cy.toDouble())
    }

    private fun inversePoint(screenX: Int, screenY: Int): Point2D.Double {
        val result = Point2D.Double()
        try {
            transform.inverseTransform(Point2D.Double(screenX.toDouble(), screenY.toDouble()), result)
        } catch (_: NoninvertibleTransformException) {
            result.setLocation(screenX.toDouble(), screenY.toDouble())
        }
        return result
    }

    private fun findNodeAt(mx: Int, my: Int): VisualNode? {
        return model.nodes.lastOrNull { n ->
            mx >= n.x && mx <= n.x + NODE_WIDTH && my >= n.y && my <= n.y + NODE_HEIGHT
        }
    }

    private fun findOutputPortAt(mx: Int, my: Int): Pair<Int, String>? {
        for (node in model.nodes) {
            val ports = node.gate.outputPorts()
            val outCount = ports.size
            for ((i, portName) in ports.withIndex()) {
                val cx = node.x + NODE_WIDTH
                val cy = node.y + NODE_HEIGHT / 2 + (i - (outCount - 1) / 2.0).toInt() * (PORT_RADIUS * 2 + 4)
                if (dist(mx, my, cx, cy) <= PORT_RADIUS + 4) {
                    return Pair(node.nodeSeq, portName)
                }
            }
        }
        return null
    }

    private fun findInputPortAt(mx: Int, my: Int): Pair<Int, String>? {
        for (node in model.nodes) {
            val ports = node.gate.inputPorts()
            if (ports.isEmpty()) continue
            for ((i, portName) in ports.withIndex()) {
                val cx = node.x
                val cy = node.y + NODE_HEIGHT / 2 + i * (PORT_RADIUS * 2 + 2) - (ports.size - 1) * (PORT_RADIUS + 1)
                if (dist(mx, my, cx, cy) <= PORT_RADIUS + 4) {
                    return Pair(node.nodeSeq, portName)
                }
            }
        }
        return null
    }

    private fun findEdgeAt(mx: Int, my: Int): VisualEdge? {
        for (edge in model.edges) {
            val fromNode = model.nodeBySeq(edge.fromSeq) ?: continue
            val toNode = model.nodeBySeq(edge.toSeq) ?: continue
            val p1 = outputPortCenter(fromNode, edge.fromPort)
            val p2 = inputPortCenter(toNode, edge.toPort)
            if (isNearBezier(mx.toDouble(), my.toDouble(), p1.x, p1.y, p2.x, p2.y, threshold = 8.0)) {
                return edge
            }
        }
        return null
    }

    private fun isNearBezier(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double, threshold: Double): Boolean {
        val steps = 20
        val ctrlOffset = (Math.abs(x2 - x1) / 2).coerceAtLeast(60.0)
        var prevX = x1
        var prevY = y1
        for (i in 1..steps) {
            val t = i.toDouble() / steps
            val bx = cubicBezier(t, x1, x1 + ctrlOffset, x2 - ctrlOffset, x2)
            val by = cubicBezier(t, y1, y1, y2, y2)
            if (distToSegment(px, py, prevX, prevY, bx, by) < threshold) return true
            prevX = bx
            prevY = by
        }
        return false
    }

    private fun cubicBezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val mt = 1 - t
        return mt * mt * mt * p0 + 3 * mt * mt * t * p1 + 3 * mt * t * t * p2 + t * t * t * p3
    }

    private fun distToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return dist(px.toInt(), py.toInt(), ax.toInt(), ay.toInt())
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        return Math.sqrt((px - (ax + tc * dx)).let { it * it } + (py - (ay + tc * dy)).let { it * it })
    }

    private fun dist(x1: Int, y1: Int, x2: Int, y2: Int): Double {
        val dx = (x1 - x2).toDouble()
        val dy = (y1 - y2).toDouble()
        return Math.sqrt(dx * dx + dy * dy)
    }

    private fun handleRightClick(e: MouseEvent, mx: Int, my: Int) {
        if (!isEditable) return
        val node = findNodeAt(mx, my) ?: return
        val popup = JPopupMenu()
        val deleteItem = JMenuItem("Delete")
        deleteItem.addActionListener {
            model.removeNode(node.nodeSeq)
            if (selectedNodeSeq == node.nodeSeq) {
                selectedNodeSeq = -1
                onNodeSelected(null)
            }
            repaint()
        }
        popup.add(deleteItem)
        val setEntryItem = JMenuItem("Set as Entry")
        setEntryItem.addActionListener {
            model.entryNodeSeq = node.nodeSeq
            model.isDirty = true
            repaint()
        }
        popup.add(setEntryItem)
        popup.show(this, e.x, e.y)
    }

    fun getSelectedNode(): VisualNode? = model.nodeBySeq(selectedNodeSeq)

    fun getViewTransform(): java.awt.geom.AffineTransform = AffineTransform(transform)

    fun zoomIn() {
        if (zoomLocked) return
        val newZoom = (zoom * 1.2).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return
        val ratio = newZoom / zoom
        zoom = newZoom
        val cx = width / 2.0
        val cy = height / 2.0
        val zoomAt = AffineTransform()
        zoomAt.translate(cx, cy)
        zoomAt.scale(ratio, ratio)
        zoomAt.translate(-cx, -cy)
        transform.preConcatenate(zoomAt)
        repaint()
    }

    fun zoomOut() {
        if (zoomLocked) return
        val newZoom = (zoom / 1.2).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) return
        val ratio = newZoom / zoom
        zoom = newZoom
        val cx = width / 2.0
        val cy = height / 2.0
        val zoomAt = AffineTransform()
        zoomAt.translate(cx, cy)
        zoomAt.scale(ratio, ratio)
        zoomAt.translate(-cx, -cy)
        transform.preConcatenate(zoomAt)
        repaint()
    }

    private fun tickHoverAlpha() {
        val step = 0.12f
        val toRemove = mutableListOf<Int>()
        for (node in model.nodes) {
            val seq = node.nodeSeq
            val current = nodeHoverAlpha.getOrDefault(seq, 0f)
            val target = if (seq == hoveredNodeSeq) 1f else 0f
            val next = if (target > current) (current + step).coerceAtMost(1f) else (current - step).coerceAtLeast(0f)
            if (next == 0f && target == 0f) {
                toRemove.add(seq)
            } else {
                nodeHoverAlpha[seq] = next
            }
        }
        toRemove.forEach { nodeHoverAlpha.remove(it) }
        if (hoveredNodeSeq == -1 && nodeHoverAlpha.isEmpty()) {
            hoverTimer.stop()
        }
    }

    private fun findLlmStarAt(mx: Int, my: Int): VisualNode? {
        for (node in model.nodes) {
            if (node.gate !is LlmGate) continue
            val cx = node.x + NODE_WIDTH - 10
            val cy = node.y + 10
            val r = 8 // hit radius slightly larger than visual radius 6
            if (dist(mx, my, cx, cy) <= r) {
                return node
            }
        }
        return null
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val modelPt = inversePoint(event.x, event.y)
        val mx = modelPt.x.toInt()
        val my = modelPt.y.toInt()
        for (node in model.nodes) {
            val inputPorts = node.gate.inputPorts()
            for ((i, portName) in inputPorts.withIndex()) {
                val cx = node.x
                val cy = node.y + NODE_HEIGHT / 2 + i * (PORT_RADIUS * 2 + 2) - (inputPorts.size - 1) * (PORT_RADIUS + 1)
                if (dist(mx, my, cx, cy) <= PORT_RADIUS + 4) {
                    return "Input: $portName"
                }
            }
            val outputPorts = node.gate.outputPorts()
            val outCount = outputPorts.size
            for ((i, portName) in outputPorts.withIndex()) {
                val cx = node.x + NODE_WIDTH
                val cy = node.y + NODE_HEIGHT / 2 + (i - (outCount - 1) / 2.0).toInt() * (PORT_RADIUS * 2 + 4)
                if (dist(mx, my, cx, cy) <= PORT_RADIUS + 4) {
                    return "Output: $portName"
                }
            }
        }
        return null
    }
}

fun snapToGrid(value: Int, gridSize: Int): Int {
    return Math.round(value.toFloat() / gridSize) * gridSize
}
