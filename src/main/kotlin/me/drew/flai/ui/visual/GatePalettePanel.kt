package me.drew.flai.ui.visual

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import me.drew.flai.ui.editor.FlaiIcons
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private val GATE_TYPES = listOf("input", "output", "llm", "logic", "tool", "bash", "read-file", "write-file")

fun filterGateTypes(query: String, types: List<String>): List<String> {
    if (query.isEmpty()) {
        return types
    }
    val lower = query.lowercase()
    return types.filter { it.lowercase().contains(lower) }
}

class GatePalettePanel(
    private val project: Project,
    parentDisposable: Disposable,
) : JPanel(BorderLayout()) {

    private var isEditable: Boolean = true

    fun setEditable(value: Boolean) {
        isEditable = value
        searchField.isEnabled = value
        collapseBtn.isEnabled = value
        pillHolder.components.forEach { it.isEnabled = value }
    }

    private var onCollapseToggled: ((Boolean) -> Unit) = {}

    fun setOnCollapseToggled(fn: (Boolean) -> Unit) {
        onCollapseToggled = fn
    }

    private var _isCollapsed: Boolean = PropertiesComponent.getInstance(project).getBoolean(persistKey(), false)

    var isCollapsed: Boolean
        get() = _isCollapsed
        private set(value) {
            _isCollapsed = value
            PropertiesComponent.getInstance(project).setValue(persistKey(), value)
            searchField.isVisible = !value
            collapseBtn.text = if (value) "▶" else "◀"
            collapseBtn.toolTipText = if (value) "Expand palette" else "Collapse palette"
            rebuildPills()
            onCollapseToggled(value)
        }

    private var searchText: String = ""

    private val searchField = JTextField().apply {
        maximumSize = Dimension(Int.MAX_VALUE, 28)
        toolTipText = "Search gates..."
    }

    private val pillHolder = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
    }

    private val noResultsLabel = JLabel("No results").apply {
        foreground = UIManager.getColor("Label.disabledForeground")
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        isVisible = false
    }

    private val collapseBtn = JButton(if (_isCollapsed) "▶" else "◀").apply {
        toolTipText = if (_isCollapsed) "Expand palette" else "Collapse palette"
        preferredSize = Dimension(24, 24)
        isBorderPainted = false
        isContentAreaFilled = false
    }

    private fun persistKey() = "flai.palette.collapsed.${project.locationHash}"

    init {
        val headerPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(collapseBtn, BorderLayout.EAST)
        }
        add(headerPanel, BorderLayout.NORTH)

        val searchPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            add(searchField, BorderLayout.CENTER)
            isVisible = !_isCollapsed
        }

        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(searchPanel, BorderLayout.NORTH)

        val listPanel = JPanel(BorderLayout())
        listPanel.add(pillHolder, BorderLayout.NORTH)
        listPanel.add(noResultsLabel, BorderLayout.CENTER)
        centerPanel.add(listPanel, BorderLayout.CENTER)

        add(centerPanel, BorderLayout.CENTER)

        // Keep searchField visibility in sync with initial state
        searchField.isVisible = !_isCollapsed

        collapseBtn.addActionListener {
            isCollapsed = !_isCollapsed
        }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onSearchChanged()
            override fun removeUpdate(e: DocumentEvent) = onSearchChanged()
            override fun changedUpdate(e: DocumentEvent) = onSearchChanged()
        })

        // Clicking anywhere on collapsed palette expands and focuses search
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (_isCollapsed) {
                    isCollapsed = false
                    searchField.requestFocusInWindow()
                }
            }
        })

        rebuildPills()

        preferredSize = if (_isCollapsed) Dimension(40, 300) else Dimension(150, 300)

        // LafManagerListener on application message bus (LAF is application-scoped)
        val connection = ApplicationManager.getApplication().messageBus.connect()
        Disposer.register(parentDisposable, connection)
        connection.subscribe(com.intellij.ide.ui.LafManagerListener.TOPIC, com.intellij.ide.ui.LafManagerListener {
            SwingUtilities.invokeLater {
                rebuildPills()
                repaint()
            }
        })
    }

    private fun onSearchChanged() {
        searchText = searchField.text
        rebuildPills()
    }

    fun rebuildPills() {
        pillHolder.removeAll()
        val filtered = filterGateTypes(searchText, GATE_TYPES)
        val showNoResults = filtered.isEmpty() && searchText.isNotEmpty()
        noResultsLabel.isVisible = showNoResults

        for (gateType in filtered) {
            val pill = GatePillPanel(gateType, _isCollapsed)
            pill.isEnabled = isEditable
            pill.alignmentX = LEFT_ALIGNMENT
            pillHolder.add(pill)
            pillHolder.add(Box.createVerticalStrut(3))
        }

        pillHolder.revalidate()
        pillHolder.repaint()
    }

    private inner class GatePillPanel(
        private val gateType: String,
        private val iconOnly: Boolean,
    ) : JPanel() {

        private var hovered: Boolean = false

        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            if (iconOnly) {
                preferredSize = Dimension(32, 28)
                maximumSize = Dimension(32, 28)
                minimumSize = Dimension(32, 28)
            } else {
                preferredSize = Dimension(120, 28)
                maximumSize = Dimension(Int.MAX_VALUE, 28)
            }
            border = BorderFactory.createEmptyBorder(3, 6, 3, 6)
            toolTipText = "Drag to canvas to create a $gateType gate"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            val icon = gateIconForType(gateType)
            val iconLabel = JLabel(icon)
            add(iconLabel)

            if (!iconOnly) {
                add(Box.createHorizontalStrut(5))
                val label = JLabel(gateType).apply {
                    font = font.deriveFont(Font.PLAIN, 12f)
                }
                add(label)
                add(Box.createHorizontalGlue())
            }

            transferHandler = object : TransferHandler() {
                override fun getSourceActions(c: JComponent): Int = COPY
                override fun createTransferable(c: JComponent): Transferable = StringSelection(gateType)
                override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {}
            }

            addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    if (!isEditable) {
                        return
                    }
                    val handler = (e.source as? JComponent)?.transferHandler ?: return
                    handler.exportAsDrag(e.source as JComponent, e, TransferHandler.COPY)
                }
            })

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hovered = true
                    repaint()
                }

                override fun mouseExited(e: MouseEvent) {
                    hovered = false
                    repaint()
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val accent = FlaiEditorTheme.accentForType(gateType)
            val bgAlpha = when {
                !isEnabled -> 20
                hovered -> 90
                else -> 45
            }
            g2.color = Color(accent.red, accent.green, accent.blue, bgAlpha)
            g2.fillRoundRect(0, 0, width, height, 8, 8)
            g2.color = Color(accent.red, accent.green, accent.blue, if (isEnabled) 200 else 80)
            g2.fillRoundRect(0, 0, 3, height, 3, 3)
            super.paintComponent(g)
        }
    }

    private fun gateIconForType(gateType: String): Icon = when (gateType) {
        "input" -> FlaiIcons.GATE_INPUT
        "output" -> FlaiIcons.GATE_OUTPUT
        "llm" -> FlaiIcons.GATE_LLM
        "logic" -> FlaiIcons.GATE_LOGIC
        "tool" -> FlaiIcons.GATE_TOOL
        "bash" -> FlaiIcons.GATE_TOOL
        "read-file" -> FlaiIcons.GATE_READ_FILE
        "write-file" -> FlaiIcons.GATE_WRITE_FILE
        else -> FlaiIcons.GATE_INPUT
    }
}
