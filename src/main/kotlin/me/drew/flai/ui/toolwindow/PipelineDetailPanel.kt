package me.drew.flai.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drew.flai.ui.editor.FlaiIcons
import me.drew.flai.ui.model.ExecutionUiState
import me.drew.flai.ui.model.InputFieldSpec
import me.drew.flai.ui.model.UiPipeline
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.util.coroutineScope
import java.awt.BorderLayout
import java.awt.Dimension
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
        val emptyPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(Box.createVerticalGlue())
            add(JLabel(AllIcons.FileTypes.Yaml).apply { alignmentX = CENTER_ALIGNMENT })
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(JBLabel("Select a pipeline to run").apply {
                alignmentX = CENTER_ALIGNMENT
                foreground = UIManager.getColor("Label.disabledForeground")
                font = font.deriveFont(Font.PLAIN, JBUI.scale(12).toFloat())
            })
            add(Box.createVerticalGlue())
        }
        inputsContainer.add(roundedWrapper(emptyPanel), BorderLayout.CENTER)
        inputsContainer.revalidate()
        inputsContainer.repaint()
    }

    private fun rebuildInputs(pipeline: UiPipeline) {
        val content = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), JBUI.scale(8))
        }

        // Pipeline header
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        content.add(buildPipelineHeader(pipeline), gbc)

        // Inputs section header + fields
        if (pipeline.inputSpecs.isNotEmpty()) {
            gbc.gridy++
            content.add(buildSectionLabel("INPUTS"), gbc)

            pipeline.inputSpecs.forEach { spec ->
                gbc.gridy++; gbc.gridwidth = 1; gbc.gridx = 0; gbc.weightx = 0.0
                content.add(JBLabel(spec.label + if (spec.required) " *" else "").apply {
                    preferredSize = Dimension(JBUI.scale(90), JBUI.scale(28))
                    font = font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())
                    foreground = UIManager.getColor("Label.disabledForeground")
                }, gbc)

                gbc.gridx = 1; gbc.weightx = 1.0
                val textField = JBTextField(inputValues[spec.key] ?: spec.defaultValue)
                textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = update()
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = update()
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = update()
                    private fun update() { inputValues[spec.key] = textField.text }
                })
                content.add(textField, gbc)
            }
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
        gbc.insets = Insets(JBUI.scale(8), JBUI.scale(8), JBUI.scale(4), JBUI.scale(8))
        content.add(runButton, gbc)

        // Filler
        gbc.gridy++; gbc.weighty = 1.0; gbc.insets = Insets(0, 0, 0, 0)
        content.add(JPanel(), gbc)

        inputsContainer.removeAll()
        inputsContainer.add(roundedWrapper(JBScrollPane(content)), BorderLayout.CENTER)
        inputsContainer.revalidate()
        inputsContainer.repaint()
    }

    private fun buildPipelineHeader(pipeline: UiPipeline): JPanel {
        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(JBUI.scale(6), 0, JBUI.scale(6), 0)
            add(JLabel(FlaiIcons.PIPELINE_FILE).apply {
                border = JBUI.Borders.emptyRight(JBUI.scale(8))
            })
            val textPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(JBLabel(pipeline.name).apply {
                    font = font.deriveFont(Font.BOLD, JBUI.scale(13).toFloat())
                })
                val meta = buildList {
                    if (pipeline.description.isNotBlank()) add(pipeline.description)
                    add("${pipeline.gateCount} gates")
                }.joinToString("  ·  ")
                add(JBLabel(meta).apply {
                    font = font.deriveFont(Font.PLAIN, JBUI.scale(11).toFloat())
                    foreground = UIManager.getColor("Label.disabledForeground")
                })
            }
            add(textPanel)
            add(Box.createHorizontalGlue())
        }
    }

    private fun buildSectionLabel(title: String): JPanel {
        return JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(JBUI.scale(4), 0, JBUI.scale(2), 0)
            add(JBLabel(title).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scale(10).toFloat())
                foreground = UIManager.getColor("Label.disabledForeground")
            }, BorderLayout.WEST)
            add(JSeparator(JSeparator.HORIZONTAL), BorderLayout.CENTER)
        }
    }
}
