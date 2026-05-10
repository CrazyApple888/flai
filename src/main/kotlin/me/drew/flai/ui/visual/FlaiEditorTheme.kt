package me.drew.flai.ui.visual

import com.intellij.ui.JBColor
import me.drew.flai.domain.model.*
import java.awt.Color

object FlaiEditorTheme {
    const val GRID_SIZE = 20 // model-space px

    // Accent colors: JBColor(lightHex, darkHex)
    val INPUT: JBColor = JBColor(0x3DAB5C, 0x2D7A42)
    val OUTPUT: JBColor = JBColor(0xE07B20, 0xB85F10)
    val LLM: JBColor = JBColor(0x3375C8, 0x2259A0)
    val LOGIC: JBColor = JBColor(0xC8A800, 0x967D00)
    val TOOL: JBColor = JBColor(0x8B44C8, 0x6A2EA0)
    val READ_FILE: JBColor = JBColor(0xC89200, 0x997000)
    val WRITE_FILE: JBColor = JBColor(0xC83050, 0xA0243E)

    // Canvas background and grid
    val CANVAS_BG: JBColor = JBColor(0xF5F5F5, 0x1E1E1E)
    val GRID_DOT_COLOR: JBColor = JBColor(Color(0, 0, 0, 22), Color(255, 255, 255, 22))

    // Node chrome
    val NODE_BG: JBColor = JBColor(0xFFFFFF, 0x2D2D2D)
    val NODE_SHADOW: JBColor = JBColor(Color(0, 0, 0, 35), Color(0, 0, 0, 80))
    val NODE_SELECTED_GLOW: JBColor = JBColor(Color(55, 120, 255, 60), Color(80, 140, 255, 80))
    val SELECTION_OUTLINE: JBColor = JBColor(Color(55, 120, 255), Color(90, 150, 255))
    val PORT_INPUT: JBColor = JBColor(Color(100, 115, 160), Color(110, 130, 190))
    val PORT_OUTPUT: JBColor = JBColor(Color(60, 170, 90), Color(60, 190, 90))

    val BRANCH_DEFAULT_COLOR: JBColor = JBColor(Color(140, 140, 140), Color(170, 170, 170))

    fun branchColor(index: Int): JBColor {
        val hue = (index * 137.508f) % 360f
        val light = Color.getHSBColor(hue / 360f, 0.65f, 0.78f)
        val dark = Color.getHSBColor(hue / 360f, 0.60f, 0.92f)
        return JBColor(light, dark)
    }

    fun accentFor(gate: Gate): Color = when (gate) {
        is InputGate -> INPUT
        is OutputGate -> OUTPUT
        is LlmGate -> LLM
        is LogicGate -> LOGIC
        is ToolGate -> TOOL
        is ReadFileGate -> READ_FILE
        is WriteFileGate -> WRITE_FILE
    }

    fun accentForType(gateType: String): Color = when (gateType) {
        "input" -> INPUT
        "output" -> OUTPUT
        "llm" -> LLM
        "logic" -> LOGIC
        "tool" -> TOOL
        "read-file" -> READ_FILE
        "write-file" -> WRITE_FILE
        else -> JBColor(0xDDDDDD, 0x444444)
    }
}
