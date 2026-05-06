package me.drew.flai.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drew.flai.ui.model.ExecutionUiState
import me.drew.flai.ui.model.InputFieldSpec
import me.drew.flai.ui.model.UiPipeline
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.util.coroutineScope
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class PipelineDetailPanel(
    private val service: FlaiPipelineUiService,
    private val disposable: Disposable,
) : JPanel(BorderLayout()) {

    private var currentPipeline: UiPipeline? = null
    private val inputValues = mutableMapOf<String, String>()
    private var executionStateJob: Job? = null
    private val inputsContainer = JPanel(BorderLayout())
    private val logPanel = ExecutionLogPanel(service, disposable)
    private val scope = disposable.coroutineScope()

    init {
        val splitter = OnePixelSplitter(true, 0.4f).apply {
            firstComponent = inputsContainer
            secondComponent = logPanel
        }
        add(splitter, BorderLayout.CENTER)
        showEmpty()
    }

    fun showPipeline(pipeline: UiPipeline) {
        currentPipeline = pipeline
        inputValues.clear()
        pipeline.inputSpecs.forEach { inputValues[it.key] = it.defaultValue }
        rebuildInputs(pipeline)
    }

    private fun showEmpty() {
        inputsContainer.removeAll()
        inputsContainer.add(JLabel("Select a pipeline to run", SwingConstants.CENTER), BorderLayout.CENTER)
        inputsContainer.revalidate()
        inputsContainer.repaint()
    }

    private fun rebuildInputs(pipeline: UiPipeline) {
        val content = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(3, 6, 3, 6)
        }

        // Header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        content.add(JLabel(pipeline.name).apply { font = font.deriveFont(Font.BOLD, 13f) }, gbc)

        if (pipeline.description.isNotBlank()) {
            gbc.gridy++
            content.add(JLabel("<html>${pipeline.description}</html>").apply {
                foreground = java.awt.Color.GRAY
            }, gbc)
        }

        gbc.gridy++
        content.add(JLabel("${pipeline.gateCount} gates").apply { foreground = java.awt.Color.GRAY }, gbc)

        gbc.gridy++; gbc.gridwidth = 2
        content.add(JSeparator(), gbc)

        // Input fields
        pipeline.inputSpecs.forEach { spec ->
            gbc.gridy++; gbc.gridwidth = 1; gbc.gridx = 0; gbc.weightx = 0.0
            content.add(JLabel(spec.label + if (spec.required) " *" else ""), gbc)

            gbc.gridx = 1; gbc.weightx = 1.0
            val textField = JTextField(inputValues[spec.key] ?: spec.defaultValue)
            textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
                private fun update() { inputValues[spec.key] = textField.text }
            })
            content.add(textField, gbc)
        }

        // Run button
        val runButton = JButton("Run Pipeline")
        executionStateJob?.cancel()
        executionStateJob = scope.launch {
            service.executionState.onEach { state ->
                withContext(Dispatchers.Main) {
                    runButton.isEnabled = state !is ExecutionUiState.Running
                    runButton.text = if (state is ExecutionUiState.Running) "Running…" else "Run Pipeline"
                }
            }.collect {}
        }
        runButton.addActionListener {
            val p = currentPipeline ?: return@addActionListener
            service.run(p, inputValues.toMap())
        }

        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        content.add(runButton, gbc)

        // Filler
        gbc.gridy++; gbc.weighty = 1.0
        content.add(JPanel(), gbc)

        inputsContainer.removeAll()
        inputsContainer.add(JBScrollPane(content), BorderLayout.CENTER)
        inputsContainer.revalidate()
        inputsContainer.repaint()
    }
}
