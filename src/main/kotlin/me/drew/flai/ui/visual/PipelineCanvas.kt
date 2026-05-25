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

internal const val NODE_WIDTH = 140
internal const val NODE_HEIGHT = 60
internal const val PORT_RADIUS = 8
internal const val ARC = 12
private const val DRAG_THRESHOLD = 2
private const val MIN_ZOOM = 0.3
private const val MAX_ZOOM = 2.5

interface PipelineCanvasListener {
    fun onNodeSelected(node: VisualNode?)
    fun onLlmStarClicked(node: VisualNode)
    fun onRepaint()
}

class PipelineCanvas(private val model: VisualPipelineModel) : JPanel() {

    private var isEditable: Boolean = true
    fun setEditable(value: Boolean) {
        isEditable = value
    }

    private var _listener: PipelineCanvasListener? = null
    fun setListener(listener: PipelineCanvasListener) {
        check(_listener == null) { "Listener already set" }
        _listener = listener
    }

    private var executionStatus: Map<String, GateStatus> = emptyMap()
    fun updateExecutionStatus(value: Map<String, GateStatus>) {
        executionStatus = value
        updateAnimationTimer()
    }

    private var zoomLocked: Boolean = false
    fun setZoomLocked(value: Boolean) {
        zoomLocked = value
    }

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
        if (isShowing) {
            repaint()
        }
    }

    // Animation (execution pulse)
    private var animTick: Int = 0
    private val animTimer = Timer(100) {
        animTick++
        repaint()
    }

    private val renderer = CanvasRenderer()

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
                        _listener?.onNodeSelected(llmStarNode)
                    }
                    _listener?.onLlmStarClicked(llmStarNode)
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
                    _listener?.onNodeSelected(node)
                    repaint()
                    return
                }

                // Check edge
                val edge = findEdgeAt(mx, my)
                if (edge != null) {
                    selectedEdge = edge
                    selectedNodeSeq = -1
                    _listener?.onNodeSelected(null)
                    repaint()
                    return
                }

                // Pan
                selectedNodeSeq = -1
                selectedEdge = null
                _listener?.onNodeSelected(null)
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
                        if (!hoverTimer.isRunning) {
                        hoverTimer.start()
                    }
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
                if (zoomLocked) {
                    return
                }
                val rotation = e.preciseWheelRotation
                if (Math.abs(rotation) < 0.1) {
                    return
                }
                val factor = if (rotation < 0) 1.1 else 1.0 / 1.1
                val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (newZoom == zoom) {
                    return
                }
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
                    if (!isEditable) {
                        return
                    }
                    val edge = selectedEdge
                    if (edge != null) {
                        model.removeEdge(edge)
                        selectedEdge = null
                        _listener?.onNodeSelected(null)
                        repaint()
                        return
                    }
                    val nodeSeq = selectedNodeSeq
                    if (nodeSeq != -1) {
                        model.removeNode(nodeSeq)
                        selectedNodeSeq = -1
                        _listener?.onNodeSelected(null)
                        repaint()
                    }
                }
            }
        })

        val undoAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                if (!isEditable) {
                    return
                }
                if (model.undo()) {
                    selectedNodeSeq = -1
                    selectedEdge = null
                    _listener?.onNodeSelected(null)
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
                if (!isEditable) {
                    return false
                }
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)
            }

            override fun importData(support: TransferSupport): Boolean {
                if (!isEditable) {
                    return false
                }
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
            "bash" -> BashGate(id = id, label = "Bash", command = "printf hello")
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

        // Compute snapped ghost position for renderer
        val ghostX = if (isGhostDragging && dragNodeSeq != -1) {
            snapToGrid(originalNodeX + ghostDragOffsetX, FlaiEditorTheme.GRID_SIZE)
        } else {
            0
        }
        val ghostY = if (isGhostDragging && dragNodeSeq != -1) {
            snapToGrid(originalNodeY + ghostDragOffsetY, FlaiEditorTheme.GRID_SIZE)
        } else {
            0
        }

        val edgeDragModelPt = inversePoint(edgeDragCurrentX, edgeDragCurrentY)

        val state = CanvasRenderState(
            selectedNodeSeq = selectedNodeSeq,
            hoveredNodeSeq = hoveredNodeSeq,
            nodeHoverAlpha = nodeHoverAlpha.toMap(),
            executionStatus = executionStatus,
            isGhostDragging = isGhostDragging,
            ghostNodeSeq = dragNodeSeq,
            ghostX = ghostX,
            ghostY = ghostY,
            isDraggingEdge = isDraggingEdge,
            edgeDragFromSeq = edgeDragFromSeq,
            edgeDragFromPort = edgeDragFromPort,
            edgeDragModelX = edgeDragModelPt.x,
            edgeDragModelY = edgeDragModelPt.y,
            animTick = animTick,
            selectedEdge = selectedEdge,
        )

        renderer.paint(g2, model, state)

        g2.transform = savedTransform
        _listener?.onRepaint()
    }

    private fun drawDotGrid(g2: Graphics2D) {
        // Map grid points from model space to screen space using current transform
        // Use screen-space step derived from model GRID_SIZE
        val modelGridSize = FlaiEditorTheme.GRID_SIZE.toDouble()
        val screenStep = (transform.scaleX * modelGridSize).coerceAtLeast(4.0)
        if (screenStep < 4.0) {
            return
        }

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
                val cy = renderer.outputPortY(node.y, i, outCount)
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
            if (ports.isEmpty()) {
                continue
            }
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
            val p1 = renderer.outputPortCenter(fromNode, edge.fromPort)
            val p2 = renderer.inputPortCenter(toNode, edge.toPort)
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
            if (distToSegment(px, py, prevX, prevY, bx, by) < threshold) {
                return true
            }
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
        if (lenSq == 0.0) {
            return dist(px.toInt(), py.toInt(), ax.toInt(), ay.toInt())
        }
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        return Math.sqrt((px - (ax + tc * dx)).let { it * it } + (py - (ay + tc * dy)).let { it * it })
    }

    private fun dist(x1: Int, y1: Int, x2: Int, y2: Int): Double {
        val dx = (x1 - x2).toDouble()
        val dy = (y1 - y2).toDouble()
        return Math.sqrt(dx * dx + dy * dy)
    }

    private fun dist(x1: Double, y1: Double, x2: Int, y2: Int): Double {
        val dx = x1 - x2.toDouble()
        val dy = y1 - y2.toDouble()
        return Math.sqrt(dx * dx + dy * dy)
    }

    private fun handleRightClick(e: MouseEvent, mx: Int, my: Int) {
        if (!isEditable) {
            return
        }
        val node = findNodeAt(mx, my) ?: return
        val popup = JPopupMenu()
        val deleteItem = JMenuItem("Delete")
        deleteItem.addActionListener {
            model.removeNode(node.nodeSeq)
            if (selectedNodeSeq == node.nodeSeq) {
                selectedNodeSeq = -1
                _listener?.onNodeSelected(null)
            }
            repaint()
        }
        popup.add(deleteItem)
        val setEntryItem = JMenuItem("Set as Entry")
        setEntryItem.addActionListener {
            model.setEntry(node.nodeSeq)
            repaint()
        }
        popup.add(setEntryItem)
        popup.show(this, e.x, e.y)
    }

    fun getSelectedNode(): VisualNode? = model.nodeBySeq(selectedNodeSeq)

    fun getViewTransform(): java.awt.geom.AffineTransform = AffineTransform(transform)

    fun zoomIn() {
        if (zoomLocked) {
            return
        }
        val newZoom = (zoom * 1.2).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) {
            return
        }
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
        if (zoomLocked) {
            return
        }
        val newZoom = (zoom / 1.2).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == zoom) {
            return
        }
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
            if (node.gate !is LlmGate) {
                continue
            }
            val cx = node.x + NODE_WIDTH - 10
            val cy = node.y + 10
            val r = 8 // hit radius slightly larger than visual radius 6
            if (dist(mx.toDouble(), my.toDouble(), cx, cy) <= r) {
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
                if (dist(mx.toDouble(), my.toDouble(), cx, cy) <= PORT_RADIUS + 4) {
                    return "Input: $portName"
                }
            }
            val outputPorts = node.gate.outputPorts()
            for ((i, portName) in outputPorts.withIndex()) {
                val cx = node.x + NODE_WIDTH
                val cy = renderer.outputPortY(node.y, i, outputPorts.size)
                if (dist(mx.toDouble(), my.toDouble(), cx, cy) <= PORT_RADIUS + 4) {
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
