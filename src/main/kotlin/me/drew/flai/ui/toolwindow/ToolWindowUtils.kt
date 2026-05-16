package me.drew.flai.ui.toolwindow

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JButton

internal fun iconButton(icon: Icon, tooltip: String, action: () -> Unit): JButton =
    object : JButton(icon) {
        private var hovered = false
        private var pressed = false

        init {
            isBorderPainted = false
            isContentAreaFilled = false
            preferredSize = Dimension(JBUI.scale(22), JBUI.scale(22))
            toolTipText = tooltip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { action() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { hovered = true; repaint() }
                override fun mouseExited(e: MouseEvent) { hovered = false; pressed = false; repaint() }
                override fun mousePressed(e: MouseEvent) { pressed = true; repaint() }
                override fun mouseReleased(e: MouseEvent) { pressed = false; repaint() }
            })
        }

        override fun paintComponent(g: Graphics) {
            if (hovered || pressed) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val alpha = if (pressed) 50 else 25
                g2.color = JBColor(Color(0, 0, 0, alpha), Color(255, 255, 255, alpha))
                g2.fillRoundRect(1, 1, width - 2, height - 2, JBUI.scale(4), JBUI.scale(4))
            }
            super.paintComponent(g)
        }
    }
