package me.drew.flai.ui.editor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.drew.flai.ui.model.ExecutionUiState
import me.drew.flai.ui.model.GateStatus
import me.drew.flai.ui.model.UiPipeline
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.service.SyncDecision
import me.drew.flai.ui.service.VisualPipelineDocumentSync
import me.drew.flai.ui.service.VisualPipelineDocumentSync.ApplyOutcome
import me.drew.flai.ui.service.VisualPipelineDocumentSync.SyncState
import me.drew.flai.ui.util.coroutineScope
import me.drew.flai.ui.visual.GatePalettePanel
import me.drew.flai.ui.visual.MinimapPanel
import me.drew.flai.ui.visual.NodePropertyPanel
import me.drew.flai.ui.visual.PipelineAutoLayout
import me.drew.flai.ui.visual.PipelineCanvas
import me.drew.flai.ui.visual.PipelineCanvasListener
import me.drew.flai.ui.visual.VisualNode
import me.drew.flai.ui.visual.VisualPipelineModel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.Timer

private const val SAVED_BANNER_MILLIS = 2000

/** Property name the platform listens to for the editor's modified flag (FileEditor.PROP_MODIFIED). */
private const val PROP_MODIFIED = "modified"

/** Swing shell of the visual pipeline editor: builds the UI and observes [VisualPipelineDocumentSync]. */
class FlaiPipelineFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val editorScope = coroutineScope()
    private val service = project.getService(FlaiPipelineUiService::class.java)
    private val sync: VisualPipelineDocumentSync = service.createDocumentSync(file, this)
    private val model: VisualPipelineModel = sync.model

    private val canvas: PipelineCanvas = PipelineCanvas(model)
    private val propertyPanel: NodePropertyPanel = NodePropertyPanel(service.toolRegistry)
    private val palettePanel: GatePalettePanel = GatePalettePanel(project, this)
    private val minimapPanel: MinimapPanel = MinimapPanel(
        model = model,
        getViewTransform = { canvas.getViewTransform() },
        getCanvasSize = { Dimension(canvas.width, canvas.height) },
    )
    private lateinit var mainSplit: OnePixelSplitter

    private val applyBtn = JButton("Apply").apply {
        toolTipText = "Validate and write visual changes to YAML now"
    }
    private val autoLayoutBtn = JButton("Auto-layout").apply {
        toolTipText = "Auto-arrange nodes"
    }
    private val fitBtn = JButton("Fit").apply {
        toolTipText = "Fit all nodes in view"
    }
    private val runBtn = JButton("Run", FlaiIcons.GUTTER_RUN).apply {
        toolTipText = "Run this pipeline"
    }
    private val cancelBtn = JButton("Cancel").apply {
        isVisible = false
        toolTipText = "Cancel running pipeline"
    }

    private val zoomInBtn = RoundedButton("+").apply {
        toolTipText = "Zoom in"
    }
    private val zoomOutBtn = RoundedButton("–").apply {
        toolTipText = "Zoom out"
    }
    private val resetZoomBtn = RoundedButton("1:1").apply {
        toolTipText = "Reset zoom"
    }
    private val lockZoomBtn = RoundedToggleButton("🔓").apply {
        toolTipText = "Lock zoom"
    }

    private val errorBanner = JBLabel("").apply {
        isVisible = false
    }
    private val savedBanner = JBLabel("Saved").apply {
        isVisible = false
        foreground = JBColor(0x2E7D32, 0x66BB6A)
    }
    private val rootPanel = JPanel(BorderLayout())

    private var savedTimer: Timer? = null
    private val pcs = PropertyChangeSupport(this)

    init {
        canvas.setListener(object : PipelineCanvasListener {
            override fun onNodeSelected(node: VisualNode?) {
                propertyPanel.showGate(node, model, canvas)
            }

            override fun onLlmStarClicked(node: VisualNode) {
                propertyPanel.scrollToLlmFieldGroup()
            }

            override fun onRepaint() {
                minimapPanel.refresh()
            }
        })

        buildUI()
        observeSync()
        observeExecution()
    }

    private fun buildUI() {
        // Toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        toolbar.preferredSize = Dimension(Int.MAX_VALUE, 38)
        applyBtn.addActionListener {
            onApply()
        }
        autoLayoutBtn.addActionListener {
            onAutoLayout()
        }
        fitBtn.addActionListener {
            canvas.resetTransform()
        }
        runBtn.addActionListener {
            onRun()
        }
        cancelBtn.addActionListener {
            service.cancelRun()
        }
        toolbar.add(applyBtn)
        toolbar.add(runBtn)
        toolbar.add(cancelBtn)
        toolbar.add(JSeparator(JSeparator.VERTICAL).apply {
            preferredSize = Dimension(1, 24)
        })
        toolbar.add(autoLayoutBtn)
        toolbar.add(fitBtn)

        val topBar = JPanel(BorderLayout())
        topBar.add(toolbar, BorderLayout.WEST)
        errorBanner.border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
        savedBanner.border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        statusPanel.add(errorBanner)
        statusPanel.add(savedBanner)
        topBar.add(statusPanel, BorderLayout.CENTER)
        // Separator line below toolbar
        topBar.add(JSeparator(JSeparator.HORIZONTAL), BorderLayout.SOUTH)

        val saveAction = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                onApply()
            }
        }
        saveAction.registerCustomShortcutSet(
            ActionManager.getInstance().getAction("SaveAll").shortcutSet,
            rootPanel,
        )

        // Wrap canvas in JLayeredPane so overlay controls can sit on top
        val canvasLayer = JLayeredPane()
        canvas.setBounds(0, 0, 800, 600)
        // Kotlin resolves add(Component, Int) to the index overload, not the layer
        // constraint — use setLayer explicitly so the overlay paints above the canvas
        canvasLayer.add(canvas)
        canvasLayer.setLayer(canvas, JLayeredPane.DEFAULT_LAYER)

        // Transparent overlay for zoom buttons and minimap
        val overlay = JPanel(null).apply {
            isOpaque = false
        }
        canvasLayer.add(overlay)
        canvasLayer.setLayer(overlay, JLayeredPane.PALETTE_LAYER)

        // Wire up zoom control buttons
        zoomInBtn.addActionListener {
            canvas.zoomIn()
        }
        zoomOutBtn.addActionListener {
            canvas.zoomOut()
        }
        resetZoomBtn.addActionListener {
            if (!lockZoomBtn.isSelected) {
                canvas.resetTransform()
            }
        }
        lockZoomBtn.addActionListener {
            val locked = lockZoomBtn.isSelected
            canvas.setZoomLocked(locked)
            lockZoomBtn.text = if (locked) "🔒" else "🔓"
            lockZoomBtn.toolTipText = if (locked) "Unlock zoom" else "Lock zoom"
            zoomInBtn.isEnabled = !locked
            zoomOutBtn.isEnabled = !locked
            resetZoomBtn.isEnabled = !locked
        }

        val zoomPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(zoomInBtn)
            add(Box.createVerticalStrut(2))
            add(zoomOutBtn)
            add(Box.createVerticalStrut(2))
            add(resetZoomBtn)
            add(Box.createVerticalStrut(2))
            add(lockZoomBtn)
        }

        // Position zoom controls top-right and minimap bottom-left in overlay
        fun repositionOverlayChildren() {
            val w = canvasLayer.width
            val h = canvasLayer.height
            overlay.setBounds(0, 0, w, h)
            val zoomSize = zoomPanel.preferredSize
            zoomPanel.setBounds(w - zoomSize.width - 6, 8, zoomSize.width, zoomSize.height)
            minimapPanel.setBounds(
                6, h - minimapPanel.preferredSize.height - 6,
                minimapPanel.preferredSize.width, minimapPanel.preferredSize.height
            )
        }

        overlay.add(zoomPanel)
        overlay.add(minimapPanel)

        canvasLayer.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                canvas.setBounds(0, 0, canvasLayer.width, canvasLayer.height)
                repositionOverlayChildren()
            }
        })

        val initialProportion = if (palettePanel.isCollapsed) 0.06f else 0.18f
        val centerSplit = OnePixelSplitter(false, 0.75f).apply {
            firstComponent = canvasLayer
            secondComponent = propertyPanel
        }

        mainSplit = OnePixelSplitter(false, initialProportion).apply {
            firstComponent = palettePanel
            secondComponent = centerSplit
        }

        palettePanel.setOnCollapseToggled { collapsed ->
            mainSplit.proportion = if (collapsed) 0.06f else 0.18f
        }

        rootPanel.add(topBar, BorderLayout.NORTH)
        rootPanel.add(mainSplit, BorderLayout.CENTER)
    }

    private fun observeSync() {
        editorScope.launch(Dispatchers.EDT) {
            sync.syncState.collect { state ->
                when (state) {
                    SyncState.Idle -> showError(null)
                    is SyncState.Saved -> showSavedInformer()
                    is SyncState.Blocked -> showError(state.message)
                    is SyncState.Error -> showError(state.message)
                }
            }
        }
        editorScope.launch(Dispatchers.EDT) {
            sync.dirty.collect { dirty ->
                pcs.firePropertyChange(PROP_MODIFIED, !dirty, dirty)
            }
        }
        editorScope.launch(Dispatchers.EDT) {
            sync.documentReloaded.collect {
                canvas.clearSelection()
                propertyPanel.showGate(null, model, canvas)
                canvas.repaint()
            }
        }
    }

    private fun observeExecution() {
        editorScope.launch(Dispatchers.EDT) {
            combine(service.executionState, service.selectedPipeline) { state, selected ->
                state to selected
            }.collect { (state, selected) ->
                if (!isThisFile(selected)) {
                    setEditingEnabled(true)
                    return@collect
                }
                when (state) {
                    is ExecutionUiState.Running -> setEditingEnabled(false)
                    is ExecutionUiState.Failed -> {
                        setEditingEnabled(true)
                        showError("Run failed: ${state.reason}")
                    }

                    else -> setEditingEnabled(true)
                }
            }
        }

        editorScope.launch(Dispatchers.EDT) {
            combine(service.logRows, service.selectedPipeline) { rows, selected ->
                rows to selected
            }.collect { (rows, selected) ->
                if (!isThisFile(selected)) {
                    canvas.updateExecutionStatus(emptyMap())
                    return@collect
                }
                val statusMap = mutableMapOf<String, GateStatus>()
                for (row in rows) {
                    val id = row.gateId ?: continue
                    statusMap[id] = row.status
                }
                canvas.updateExecutionStatus(statusMap)
            }
        }
    }

    private fun isThisFile(pipeline: UiPipeline?): Boolean =
        pipeline?.filePath?.toString() == file.path

    private fun onApply() {
        presentApplyOutcome(sync.apply())
    }

    private fun presentApplyOutcome(outcome: ApplyOutcome) {
        when (outcome) {
            ApplyOutcome.Written -> Unit
            is ApplyOutcome.ValidationFailed -> {
                val msg = outcome.errors.joinToString("\n") { "• ${it.gateId} / ${it.field}: ${it.message}" }
                JOptionPane.showMessageDialog(rootPanel, msg, "Validation Errors", JOptionPane.ERROR_MESSAGE)
            }

            ApplyOutcome.NeedsNormalizeConfirmation -> {
                val choice = JOptionPane.showConfirmDialog(
                    rootPanel,
                    "Applying will normalize the YAML file.\nYAML comments and custom formatting may be lost.\n\nProceed?",
                    "Apply Visual Changes",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                )
                if (choice == JOptionPane.OK_OPTION) {
                    presentApplyOutcome(sync.acceptNormalizeAndApply())
                }
            }

            is ApplyOutcome.WriteFailed -> showError(outcome.message)
        }
    }

    private fun onRun() {
        when (val decision = sync.flushSync()) {
            is SyncDecision.Blocked -> showError(decision.message)
            SyncDecision.Proceed -> service.runFromFile(file.path)
        }
    }

    private fun showSavedInformer() {
        savedTimer?.stop()
        showError(null)
        savedBanner.isVisible = true
        savedTimer = Timer(SAVED_BANNER_MILLIS) {
            savedBanner.isVisible = false
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun onAutoLayout() {
        val pipeline = model.toPipeline()
        val layout = PipelineAutoLayout.compute(pipeline)
        for (node in model.nodes) {
            val pos = layout.positions[node.gateId] ?: continue
            model.moveNode(node.nodeSeq, pos.first, pos.second)
        }
        canvas.repaint()
    }

    private fun showError(msg: String?) {
        errorBanner.text = msg ?: ""
        errorBanner.isVisible = msg != null
        if (msg != null) {
            savedTimer?.stop()
            savedBanner.isVisible = false
        }
    }

    private fun setEditingEnabled(enabled: Boolean) {
        applyBtn.isEnabled = enabled
        autoLayoutBtn.isEnabled = enabled
        canvas.setEditable(enabled)
        propertyPanel.setEditable(enabled)
        palettePanel.setEditable(enabled)
        runBtn.isVisible = enabled
        cancelBtn.isVisible = !enabled
    }

    // FileEditor interface

    override fun getComponent(): JComponent = rootPanel
    override fun getPreferredFocusedComponent(): JComponent = canvas
    override fun getName(): String = "Visual"
    override fun isModified(): Boolean = sync.dirty.value
    override fun isValid(): Boolean = true
    override fun getFile(): VirtualFile = file

    override fun getState(level: FileEditorStateLevel): FileEditorState =
        FileEditorState { _, _ -> false }

    override fun setState(state: FileEditorState) {}

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        pcs.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        pcs.removePropertyChangeListener(listener)
    }

    override fun dispose() {
        savedTimer?.stop()
    }
}
