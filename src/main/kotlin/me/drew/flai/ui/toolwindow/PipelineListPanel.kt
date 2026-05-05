package me.drew.flai.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drew.flai.ui.model.UiPipeline
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.util.coroutineScope
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

class PipelineListPanel(
    private val service: FlaiPipelineUiService,
    disposable: Disposable,
    private val onSelect: (UiPipeline) -> Unit,
) : JPanel(BorderLayout()) {

    private val listModel = CollectionListModel<UiPipeline>()
    private val scope = disposable.coroutineScope()
    private var suppressSelectionEvent = false

    private val jbList = JBList(listModel).apply {
        cellRenderer = PipelineCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        addListSelectionListener { e ->
            if (!e.valueIsAdjusting && !suppressSelectionEvent) {
                selectedValue?.let { pipeline ->
                    service.selectPipeline(pipeline)
                    onSelect(pipeline)
                }
            }
        }
    }

    init {
        val refreshButton = JButton("Refresh").apply {
            addActionListener { service.refresh() }
        }
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel("Pipelines"))
            add(refreshButton)
        }
        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(jbList), BorderLayout.CENTER)

        // Update list when pipelines change
        scope.launch {
            service.pipelines.onEach { pipelines ->
                withContext(Dispatchers.Main) {
                    suppressSelectionEvent = true
                    val previouslySelected = service.selectedPipeline.value?.id
                    listModel.replaceAll(pipelines)
                    // Restore selection after list update
                    val idx = pipelines.indexOfFirst { it.id == previouslySelected }
                    if (idx >= 0) jbList.selectedIndex = idx
                    suppressSelectionEvent = false
                }
            }.collect {}
        }

        // Sync external selection changes (e.g. from gutter action)
        scope.launch {
            service.selectedPipeline.onEach { selected ->
                withContext(Dispatchers.Main) {
                    if (selected == null) return@withContext
                    val idx = listModel.items.indexOfFirst { it.id == selected.id }
                    if (idx >= 0 && jbList.selectedIndex != idx) {
                        suppressSelectionEvent = true
                        jbList.selectedIndex = idx
                        jbList.ensureIndexIsVisible(idx)
                        suppressSelectionEvent = false
                        onSelect(selected)
                    }
                }
            }.collect {}
        }
    }

    private class PipelineCellRenderer : ColoredListCellRenderer<UiPipeline>() {
        override fun customizeCellRenderer(
            list: JList<out UiPipeline>,
            value: UiPipeline,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            append(value.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            append("  ${value.gateCount} gates", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
        }
    }
}
