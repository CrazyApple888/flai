package me.drew.flai.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drew.flai.ui.editor.FlaiIcons
import me.drew.flai.ui.model.UiPipeline
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.util.coroutineScope
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*

class PipelineListPanel(
    private val service: FlaiPipelineUiService,
    disposable: Disposable,
    private val onSelectionChanged: (UiPipeline?) -> Unit,
) : JPanel(BorderLayout()) {

    private val listModel = CollectionListModel<UiPipeline>()
    private val scope = disposable.coroutineScope()
    private var suppressSelectionEvent = false

    private val jbList = JBList(listModel).apply {
        cellRenderer = PipelineCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        addListSelectionListener { e ->
            if (!e.valueIsAdjusting && !suppressSelectionEvent) {
                // Publish only; the selectedPipeline collector below drives onSelectionChanged,
                // so the detail panel is rebuilt exactly once per selection change.
                selectedValue?.let { pipeline ->
                    service.selectPipeline(pipeline)
                }
            }
        }
    }

    init {
        val refreshButton = iconButton(AllIcons.Actions.Refresh, "Refresh") { service.refresh() }
        val header = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8))
            add(JBLabel("PIPELINES").apply {
                font = font.deriveFont(Font.BOLD, JBUI.scale(10).toFloat())
                foreground = UIManager.getColor("Label.disabledForeground")
            }, BorderLayout.WEST)
            add(JSeparator(JSeparator.HORIZONTAL), BorderLayout.CENTER)
            add(refreshButton, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)
        add(roundedWrapper(JBScrollPane(jbList)), BorderLayout.CENTER)

        // Update list when pipelines change
        scope.launch {
            service.pipelines.onEach { pipelines ->
                withContext(Dispatchers.Main) {
                    suppressSelectionEvent = true
                    val previouslySelected = service.selectedPipeline.value
                    listModel.replaceAll(pipelines)
                    // Restore selection after list update: by pipeline id first, then by file path
                    val restoredIndex = indexOfMatch(pipelines, previouslySelected)
                    if (restoredIndex >= 0) {
                        jbList.selectedIndex = restoredIndex
                    } else {
                        jbList.clearSelection()
                    }
                    suppressSelectionEvent = false
                }
            }.collect {}
        }

        // Sync external selection changes (e.g. from gutter action) and cleared selections
        scope.launch {
            service.selectedPipeline.onEach { selected ->
                withContext(Dispatchers.Main) {
                    val selectedIndex = indexOfMatch(listModel.items, selected)
                    if (jbList.selectedIndex != selectedIndex) {
                        suppressSelectionEvent = true
                        if (selectedIndex >= 0) {
                            jbList.selectedIndex = selectedIndex
                            jbList.ensureIndexIsVisible(selectedIndex)
                        } else {
                            jbList.clearSelection()
                        }
                        suppressSelectionEvent = false
                    }
                    // Always notify: the same row may have become invalid (or valid) in place
                    onSelectionChanged(selected)
                }
            }.collect {}
        }
    }

    /** Index of [target] in [pipelines], matched by pipeline id and then by file path; -1 when absent. */
    private fun indexOfMatch(pipelines: List<UiPipeline>, target: UiPipeline?): Int {
        if (target == null) {
            return -1
        }
        val byId = pipelines.indexOfFirst { it.id == target.id }
        if (byId >= 0) {
            return byId
        }
        return pipelines.indexOfFirst { it.filePath != null && it.filePath == target.filePath }
    }

    private class PipelineCellRenderer : ColoredListCellRenderer<UiPipeline>() {
        override fun customizeCellRenderer(
            list: JList<out UiPipeline>,
            value: UiPipeline,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            val parseError = value.parseError
            if (parseError != null) {
                icon = AllIcons.General.Warning
                append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("  invalid YAML", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                toolTipText = asHtmlMultiline(parseError)
                return
            }
            val validationIssues = value.validationIssues
            if (validationIssues.isNotEmpty()) {
                icon = AllIcons.General.Warning
                append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                append("  ${issueCountLabel(validationIssues.size)}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                toolTipText = asHtmlMultiline(validationIssues.joinToString("\n"))
                return
            }
            icon = FlaiIcons.PIPELINE_FILE
            append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            append("  ${value.gateCount} gates", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            toolTipText = null
        }
    }
}
