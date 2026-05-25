package me.drew.flai.ui.editor

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import me.drew.flai.infrastructure.pipeline.YamlPipelineSerializer
import me.drew.flai.ui.model.ExecutionUiState
import me.drew.flai.ui.model.GateStatus
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.visual.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.io.File
import javax.swing.*

class FlaiPipelineFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val parser = YamlPipelineParser()
    private val serializer = YamlPipelineSerializer()

    private val document = FileDocumentManager.getInstance().getDocument(file)
    private val service = project.getService(FlaiPipelineUiService::class.java)

    private val sidecarFile = run {
        val relativePath = file.path
            .removePrefix(project.basePath ?: "")
            .replace(File.separatorChar, '_')
            .trimStart('_')
        val layoutDir = File(PathManager.getSystemPath(), "flai/layouts/${project.locationHash}")
        layoutDir.mkdirs()
        File(layoutDir, "$relativePath.layout.json")
    }
    private val layoutStore = LayoutStore(sidecarFile)

    private val model: VisualPipelineModel
    private val canvas: PipelineCanvas
    private val propertyPanel: NodePropertyPanel
    private val palettePanel: GatePalettePanel
    private val minimapPanel: MinimapPanel
    private lateinit var mainSplit: OnePixelSplitter

    private val applyBtn = JButton("Apply").apply {
        toolTipText = "Apply visual changes to YAML"
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

    private val zoomInBtn = JButton("+").apply {
        toolTipText = "Zoom in"
    }
    private val zoomOutBtn = JButton("–").apply {
        toolTipText = "Zoom out"
    }
    private val resetZoomBtn = JButton("1:1").apply {
        toolTipText = "Reset zoom"
    }
    private val lockZoomBtn = JToggleButton("🔓").apply {
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

    private var applyInProgress = false
    private var debounceJob: Job? = null
    private var savedTimer: Timer? = null
    private val pcs = PropertyChangeSupport(this)

    private val docListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (applyInProgress) {
                return
            }
            debounceJob?.cancel()
            debounceJob = editorScope.launch {
                delay(300)
                withContext(Dispatchers.Main) {
                    reloadFromDocument()
                }
            }
        }
    }

    init {
        model = buildModel(document?.text ?: "")

        canvas = PipelineCanvas(model)
        propertyPanel = NodePropertyPanel(service.toolRegistry)
        palettePanel = GatePalettePanel(project, this)
        minimapPanel = MinimapPanel(
            model = model,
            getViewTransform = { canvas.getViewTransform() },
            getCanvasSize = { Dimension(canvas.width, canvas.height) },
        )

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

        document?.addDocumentListener(docListener)

        buildUI()
        subscribeToExecution()
    }

    private fun buildModel(yamlText: String): VisualPipelineModel {
        return try {
            val pipeline = parser.parse(yamlText)
            val positions = layoutStore.load()
            VisualPipelineModel.fromPipeline(pipeline, positions)
        } catch (_: Exception) {
            VisualPipelineModel()
        }
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
        canvasLayer.add(canvas, JLayeredPane.DEFAULT_LAYER)

        // Transparent overlay for zoom buttons and minimap
        val overlay = JPanel(null).apply {
            isOpaque = false
        }
        canvasLayer.add(overlay, JLayeredPane.PALETTE_LAYER)

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
            val zw = 40
            val zh = 140
            zoomPanel.setBounds(w - zw - 6, 8, zw, zh)
            minimapPanel.setBounds(6, h - minimapPanel.preferredSize.height - 6,
                minimapPanel.preferredSize.width, minimapPanel.preferredSize.height)
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

    private fun reloadFromDocument() {
        val text = document?.text ?: return
        try {
            val pipeline = parser.parse(text)
            val newModel = VisualPipelineModel.fromPipeline(pipeline, layoutStore.load())
            model.replaceWith(newModel)
            model.clearHistory()
            canvas.repaint()
            showError(null)
        } catch (e: Exception) {
            showError("Parse error: ${e.message}")
        }
    }

    private fun onApply() {
        val result = VisualPipelineValidator.validate(model)
        if (!result.isValid) {
            val msg = result.errors.joinToString("\n") { "• ${it.gateId} / ${it.field}: ${it.message}" }
            JOptionPane.showMessageDialog(rootPanel, msg, "Validation Errors", JOptionPane.ERROR_MESSAGE)
            return
        }

        val yamlText = document?.text ?: ""
        val propsKey = "flai.apply.warned.${file.path}"
        val alreadyWarned = PropertiesComponent.getInstance().getBoolean(propsKey, false)
        if (!alreadyWarned && (yamlText.contains('#') || yamlText.contains("---"))) {
            val choice = JOptionPane.showConfirmDialog(
                rootPanel,
                "Applying will normalize the YAML file.\nYAML comments and custom formatting may be lost.\n\nProceed?",
                "Apply Visual Changes",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
            if (choice != JOptionPane.OK_OPTION) {
                return
            }
            PropertiesComponent.getInstance().setValue(propsKey, true)
        }

        val serialized = serializer.serialize(model.toPipeline())
        applyInProgress = true
        try {
            WriteCommandAction.runWriteCommandAction(project, "Apply Visual Pipeline", null, Runnable {
                document?.setText(serialized)
            })
        } finally {
            applyInProgress = false
        }

        val positions = model.nodes.associate { it.gateId to GatePosition(it.x, it.y) }
        layoutStore.save(positions)
        model.clearDirty()
        pcs.firePropertyChange("modified", null, null)
        showSavedInformer()
    }

    private fun showSavedInformer() {
        savedTimer?.stop()
        showError(null)
        savedBanner.isVisible = true
        savedTimer = Timer(2000) {
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

    private fun onRun() {
        FileDocumentManager.getInstance().saveDocument(document ?: return)
        service.runFromFile(file.path)
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

    private fun subscribeToExecution() {
        editorScope.launch {
            service.executionState.onEach { state ->
                withContext(Dispatchers.Main) {
                    when (state) {
                        is ExecutionUiState.Running -> setEditingEnabled(false)
                        is ExecutionUiState.Failed -> {
                            setEditingEnabled(true)
                            showError("Run failed: ${state.reason}")
                        }
                        else -> setEditingEnabled(true)
                    }
                }
            }.collect()
        }

        editorScope.launch {
            service.logRows.onEach { rows ->
                withContext(Dispatchers.Main) {
                    val statusMap = mutableMapOf<String, GateStatus>()
                    for (row in rows) {
                        statusMap[row.gateName] = row.status
                    }
                    canvas.updateExecutionStatus(statusMap)
                }
            }.collect()
        }
    }

    // FileEditor interface

    override fun getComponent(): JComponent = rootPanel
    override fun getPreferredFocusedComponent(): JComponent = canvas
    override fun getName(): String = "Visual"
    override fun isModified(): Boolean = model.isDirty
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
        document?.removeDocumentListener(docListener)
        savedTimer?.stop()
        editorScope.cancel()
    }
}
