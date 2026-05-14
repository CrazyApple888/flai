package me.drew.flai.ui.visual

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBScrollPane
import me.drew.flai.domain.model.*
import me.drew.flai.infrastructure.tool.IdeToolRegistry
import java.awt.*
import javax.swing.*

class NodePropertyPanel(private val toolRegistry: IdeToolRegistry) : JPanel(BorderLayout()) {

    private var isEditable: Boolean = true

    fun setEditable(value: Boolean) {
        isEditable = value
        refreshEnabled()
    }

    private var currentNode: VisualNode? = null
    private var currentModel: VisualPipelineModel? = null
    private var canvas: PipelineCanvas? = null

    private val innerPanel = JPanel()
    private val scrollPane = JBScrollPane(innerPanel)
    private val editableComponents = mutableListOf<JComponent>()
    private var firstFocusTarget: JComponent? = null

    private val sections = GatePropertySections(
        toolRegistry = toolRegistry,
        onGateUpdated = { nodeSeq, gate ->
            currentModel?.updateGate(nodeSeq, gate)
            canvas?.repaint()
        },
        onRepaint = { canvas?.repaint() },
        onRefreshPanel = {
            val node = currentNode
            val model = currentModel
            val c = canvas
            if (node != null && model != null && c != null) {
                val freshNode = model.nodeBySeq(node.nodeSeq)
                showGate(freshNode, model, c)
            }
        },
    )

    init {
        innerPanel.layout = BoxLayout(innerPanel, BoxLayout.Y_AXIS)
        innerPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        preferredSize = Dimension(280, 400)
        minimumSize = Dimension(220, 200)
        add(scrollPane, BorderLayout.CENTER)
        showEmpty()
    }

    fun showGate(node: VisualNode?, model: VisualPipelineModel, canvasRef: PipelineCanvas) {
        currentNode = node
        currentModel = model
        canvas = canvasRef
        editableComponents.clear()
        firstFocusTarget = null
        innerPanel.removeAll()

        if (node == null) {
            showEmpty()
            return
        }

        // Basic Info card
        val basicResult = sections.buildBasicInfoFields(node.nodeSeq, node, model)
        editableComponents.addAll(basicResult.editableComponents)
        for (card in basicResult.cards) {
            innerPanel.add(card)
        }

        val gateResult: SectionResult = when (val gate = node.gate) {
            is InputGate -> sections.buildInputGateFields(node.nodeSeq, gate)
            is OutputGate -> sections.buildOutputGateFields(node.nodeSeq, gate)
            is LlmGate -> sections.buildLlmGateFields(node.nodeSeq, gate)
            is LogicGate -> sections.buildLogicGateFields(node.nodeSeq, gate, model)
            is ToolGate -> sections.buildToolGateFields(node.nodeSeq, gate)
            is BashGate -> sections.buildBashGateFields(node.nodeSeq, gate)
            is ReadFileGate -> sections.buildReadFileGateFields(node.nodeSeq, gate)
            is WriteFileGate -> sections.buildWriteFileGateFields(node.nodeSeq, gate)
        }
        editableComponents.addAll(gateResult.editableComponents)
        for (card in gateResult.cards) {
            innerPanel.add(card)
        }
        firstFocusTarget = gateResult.firstFocusTarget

        refreshEnabled()
        innerPanel.revalidate()
        innerPanel.repaint()
    }

    fun scrollToLlmFieldGroup() {
        val target = firstFocusTarget ?: return
        SwingUtilities.invokeLater {
            val rect = SwingUtilities.convertRectangle(target.parent ?: target, target.bounds, innerPanel)
            scrollPane.viewport.scrollRectToVisible(rect)
            target.requestFocusInWindow()
        }
    }

    private fun showEmpty() {
        firstFocusTarget = null
        innerPanel.removeAll()
        val emptyPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            alignmentX = LEFT_ALIGNMENT
            isOpaque = false
            add(Box.createVerticalStrut(24))
            val iconLabel = JLabel(AllIcons.General.Information).apply {
                alignmentX = CENTER_ALIGNMENT
            }
            add(iconLabel)
            add(Box.createVerticalStrut(8))
            val msgLabel = JLabel("Select a node to view its properties").apply {
                alignmentX = CENTER_ALIGNMENT
                foreground = UIManager.getColor("Label.disabledForeground")
                font = font.deriveFont(Font.PLAIN, 12f)
            }
            add(msgLabel)
        }
        innerPanel.add(emptyPanel)
        innerPanel.revalidate()
        innerPanel.repaint()
    }

    private fun refreshEnabled() {
        editableComponents.forEach { it.isEnabled = isEditable }
    }
}
