package me.drew.flai.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.drew.flai.ui.model.GateRow
import me.drew.flai.ui.model.GateStatus
import me.drew.flai.ui.service.FlaiPipelineUiService
import me.drew.flai.ui.util.coroutineScope
import java.awt.BorderLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class ExecutionLogPanel(
    private val service: FlaiPipelineUiService,
    disposable: Disposable,
) : JPanel(BorderLayout()) {

    private val rowModel = CollectionListModel<GateRow>()
    private val scope = disposable.coroutineScope()

    private val logList = JBList(rowModel).apply {
        cellRenderer = GateRowRenderer()
        layoutOrientation = JList.VERTICAL
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                val index = locationToIndex(e.point)
                if (index < 0) return
                val row = rowModel.getElementAt(index)
                if (row.status == GateStatus.OUTPUT) {
                    showValuePopup(row)
                }
            }
        })
    }

    init {
        val clearButton = iconButton(AllIcons.Actions.GC, "Clear") { service.clearLog() }
        val header = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            isOpaque = false
            border = JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(8), JBUI.scale(6), JBUI.scale(8))
            add(JBLabel("EXECUTION LOG").apply {
                font = font.deriveFont(Font.BOLD, JBUI.scale(10).toFloat())
                foreground = UIManager.getColor("Label.disabledForeground")
            }, BorderLayout.WEST)
            add(JSeparator(JSeparator.HORIZONTAL), BorderLayout.CENTER)
            add(clearButton, BorderLayout.EAST)
        }
        add(header, BorderLayout.NORTH)
        add(roundedWrapper(JBScrollPane(logList)), BorderLayout.CENTER)

        scope.launch {
            service.logRows.onEach { rows ->
                withContext(Dispatchers.Main) {
                    rowModel.replaceAll(rows)
                    if (rows.isNotEmpty()) logList.ensureIndexIsVisible(rows.size - 1)
                }
            }.collect {}
        }
    }

    private fun showValuePopup(row: GateRow) {
        val fullText = row.outputValue ?: row.outputLabel ?: return
        val textArea = JBTextArea(fullText).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(12f))
            border = JBUI.Borders.empty(8)
        }
        val scroll = JBScrollPane(textArea)
        val parentWindow = SwingUtilities.getWindowAncestor(this)
        val dialog = JDialog(parentWindow, row.gateName, java.awt.Dialog.ModalityType.APPLICATION_MODAL).apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            isResizable = true
            contentPane.add(scroll, BorderLayout.CENTER)
            setSize(JBUI.scale(600), JBUI.scale(300))
            setLocationRelativeTo(parentWindow)
        }
        dialog.isVisible = true
    }

    private class GateRowRenderer : ColoredListCellRenderer<GateRow>() {
        override fun customizeCellRenderer(
            list: JList<out GateRow>,
            row: GateRow,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            when (row.status) {
                GateStatus.OUTPUT -> {
                    icon = AllIcons.Nodes.Variable
                    append(row.outputLabel ?: "${row.gateName} = ?", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
                GateStatus.RUNNING -> {
                    icon = AnimatedIcon.Default.INSTANCE
                    append(row.gateName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  running…", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
                GateStatus.SUCCESS -> {
                    icon = AllIcons.RunConfigurations.TestPassed
                    append(row.gateName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    row.durationMs?.let {
                        append("  ${it}ms", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                    }
                }
                GateStatus.FAILURE -> {
                    icon = AllIcons.RunConfigurations.TestFailed
                    append(row.gateName, SimpleTextAttributes.ERROR_ATTRIBUTES)
                    row.message?.let {
                        append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }
            }
        }
    }
}
