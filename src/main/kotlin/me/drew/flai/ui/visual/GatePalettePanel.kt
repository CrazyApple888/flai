package me.drew.flai.ui.visual

import com.intellij.ui.JBColor
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.*

class GatePalettePanel : JPanel(BorderLayout()) {

    /** Disable drag-to-canvas during execution (EC-9a). */
    var isEditable: Boolean = true
        set(value) {
            field = value
            for (component in buttonPanel.components) {
                component.isEnabled = value
            }
        }

    private val buttonPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val gateTypes = listOf(
        "input", "output", "llm", "logic", "tool", "read-file", "write-file"
    )

    init {
        val header = JLabel("Gate Palette").apply {
            font = font.deriveFont(Font.BOLD)
            border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
        }
        add(header, BorderLayout.NORTH)

        for (gateType in gateTypes) {
            val btn = createPaletteButton(gateType)
            buttonPanel.add(btn)
            buttonPanel.add(Box.createVerticalStrut(2))
        }
        buttonPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        add(buttonPanel, BorderLayout.CENTER)

        preferredSize = Dimension(130, 300)
    }

    private fun createPaletteButton(gateType: String): JButton {
        val btn = JButton(gateType).apply {
            alignmentX = CENTER_ALIGNMENT
            maximumSize = Dimension(120, 28)
            preferredSize = Dimension(120, 28)
            toolTipText = "Drag to canvas to create a $gateType gate"
            background = gateTypeColor(gateType)
        }
        btn.transferHandler = object : TransferHandler() {
            override fun getSourceActions(c: JComponent): Int = COPY
            override fun createTransferable(c: JComponent): Transferable = StringSelection(gateType)
            override fun exportDone(source: JComponent?, data: Transferable?, action: Int) {}
        }
        btn.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (!isEditable) return
                val handler = (e.source as? JComponent)?.transferHandler ?: return
                handler.exportAsDrag(e.source as JComponent, e, TransferHandler.COPY)
            }
        })
        return btn
    }

    private fun gateTypeColor(gateType: String): java.awt.Color = when (gateType) {
        "input" -> JBColor(0xA8D8A8, 0x2D6A2D)
        "output" -> JBColor(0xF4A460, 0x8B4513)
        "llm" -> JBColor(0xADD8E6, 0x1C6B8A)
        "logic" -> JBColor(0xFFD700, 0x7A6000)
        "tool" -> JBColor(0xDDA0DD, 0x5C3063)
        "read-file" -> JBColor(0xF0E68C, 0x5C5000)
        "write-file" -> JBColor(0xFFB6C1, 0x7A2030)
        else -> JBColor(0xDDDDDD, 0x444444)
    }
}
