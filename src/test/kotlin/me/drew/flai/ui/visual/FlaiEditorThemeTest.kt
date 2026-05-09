package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class FlaiEditorThemeTest {

    // Seven distinct gate instances for testing accent colors
    private val inputGate = InputGate(id = GateId("i"), label = "Input")
    private val outputGate = OutputGate(id = GateId("o"), label = "Output")
    private val llmGate = LlmGate(
        id = GateId("l"), label = "LLM",
        promptTemplate = "",
        endpointConfig = LlmEndpointConfig("", "", ""),
    )
    private val logicGate = LogicGate(id = GateId("lg"), label = "Logic", branches = emptyList())
    private val toolGate = ToolGate(id = GateId("t"), label = "Tool", toolName = "")
    private val readFileGate = ReadFileGate(id = GateId("r"), label = "Read", path = "", outputKey = "out")
    private val writeFileGate = WriteFileGate(id = GateId("w"), label = "Write", path = "", contentKey = "c")

    private val allGates: List<Gate> = listOf(inputGate, outputGate, llmGate, logicGate, toolGate, readFileGate, writeFileGate)
    private val allTypes: List<String> = listOf("input", "output", "llm", "logic", "tool", "read-file", "write-file")

    @Test
    fun `accentFor returns non-null for all gate types`() {
        for (gate in allGates) {
            assertNotNull("accentFor(${gate.javaClass.simpleName}) returned null", FlaiEditorTheme.accentFor(gate))
        }
    }

    @Test
    fun `accentFor returns distinct colors for all gate types`() {
        val colors = allGates.map { FlaiEditorTheme.accentFor(it) }
        val distinct = colors.toSet()
        assertEquals("Expected 7 distinct accent colors, got ${distinct.size}", 7, distinct.size)
    }

    @Test
    fun `accentForType returns non-null for all type strings`() {
        for (type in allTypes) {
            assertNotNull("accentForType($type) returned null", FlaiEditorTheme.accentForType(type))
        }
    }

    @Test
    fun `accentForType returns distinct colors for all type strings`() {
        val colors = allTypes.map { FlaiEditorTheme.accentForType(it) }
        val distinct = colors.toSet()
        assertEquals("Expected 7 distinct accent colors from accentForType, got ${distinct.size}", 7, distinct.size)
    }

    @Test
    fun `accentForType returns fallback for unknown type`() {
        val color = FlaiEditorTheme.accentForType("unknown-type")
        assertNotNull(color)
    }

    @Test
    fun `GRID_SIZE is positive`() {
        assertTrue(FlaiEditorTheme.GRID_SIZE > 0)
    }
}
