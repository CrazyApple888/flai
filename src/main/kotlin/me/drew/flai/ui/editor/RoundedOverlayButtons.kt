package me.drew.flai.ui.editor

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JToggleButton

internal class RoundedButton(text: String) : JButton(text) {
    init {
        configureRounded(this)
    }

    override fun paintComponent(g: Graphics) {
        paintRoundedBackground(g, this)
        super.paintComponent(g)
    }
}

internal class RoundedToggleButton(text: String) : JToggleButton(text) {
    init {
        configureRounded(this)
    }

    override fun paintComponent(g: Graphics) {
        paintRoundedBackground(g, this)
        super.paintComponent(g)
    }
}

private fun configureRounded(button: AbstractButton) {
    button.isOpaque = false
    button.isContentAreaFilled = false
    button.isFocusPainted = false
    button.isRolloverEnabled = true
    button.border = JBUI.Borders.empty(4)
    button.alignmentX = Component.CENTER_ALIGNMENT
    button.preferredSize = JBUI.size(44, 28)
    button.maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(28))
}

private fun paintRoundedBackground(g: Graphics, button: AbstractButton) {
    val g2 = g.create() as Graphics2D
    try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val arc = JBUI.scale(10)
        g2.color = backgroundFor(button)
        g2.fillRoundRect(0, 0, button.width - 1, button.height - 1, arc, arc)
        g2.color = JBColor.border()
        g2.drawRoundRect(0, 0, button.width - 1, button.height - 1, arc, arc)
    } finally {
        g2.dispose()
    }
}

private fun backgroundFor(button: AbstractButton): Color {
    val base = button.background ?: JBColor.PanelBackground
    val model = button.model
    return when {
        model.isPressed || model.isSelected -> ColorUtil.darker(base, 1)
        model.isRollover -> ColorUtil.brighter(base, 1)
        else -> base
    }
}
