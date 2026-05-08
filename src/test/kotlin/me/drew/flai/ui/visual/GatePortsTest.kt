package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatePortsTest {

    @Test
    fun `InputGate has no input ports`() {
        val gate = InputGate(id = GateId("g"), label = "G")
        assertTrue(gate.inputPorts().isEmpty())
    }

    @Test
    fun `InputGate has one output port named out`() {
        val gate = InputGate(id = GateId("g"), label = "G")
        assertEquals(listOf("out"), gate.outputPorts())
    }

    @Test
    fun `OutputGate has one input port named in`() {
        val gate = OutputGate(id = GateId("g"), label = "G")
        assertEquals(listOf("in"), gate.inputPorts())
    }

    @Test
    fun `OutputGate has one output port named out`() {
        val gate = OutputGate(id = GateId("g"), label = "G")
        assertEquals(listOf("out"), gate.outputPorts())
    }

    @Test
    fun `LlmGate has one input port named in`() {
        val gate = LlmGate(
            id = GateId("g"),
            label = "G",
            promptTemplate = "x",
            endpointConfig = LlmEndpointConfig("url", "cred", "model"),
        )
        assertEquals(listOf("in"), gate.inputPorts())
    }

    @Test
    fun `LlmGate has one output port named out`() {
        val gate = LlmGate(
            id = GateId("g"),
            label = "G",
            promptTemplate = "x",
            endpointConfig = LlmEndpointConfig("url", "cred", "model"),
        )
        assertEquals(listOf("out"), gate.outputPorts())
    }

    @Test
    fun `LogicGate has one input port named in`() {
        val gate = LogicGate(
            id = GateId("g"),
            label = "G",
            branches = listOf(Branch("yes", BranchCondition.Always)),
            defaultPort = "no",
        )
        assertEquals(listOf("in"), gate.inputPorts())
    }

    @Test
    fun `LogicGate with two branches and defaultPort has four output ports`() {
        val gate = LogicGate(
            id = GateId("g"),
            label = "G",
            branches = listOf(
                Branch("high", BranchCondition.Comparison("score", ComparisonOp.GTE, "80")),
                Branch("low", BranchCondition.Comparison("score", ComparisonOp.LT, "80")),
            ),
            defaultPort = "default",
        )
        val ports = gate.outputPorts()
        assertEquals(3, ports.size)
        assertTrue(ports.contains("high"))
        assertTrue(ports.contains("low"))
        assertTrue(ports.contains("default"))
    }

    @Test
    fun `LogicGate with null defaultPort omits it from output ports`() {
        val gate = LogicGate(
            id = GateId("g"),
            label = "G",
            branches = listOf(Branch("yes", BranchCondition.Always)),
            defaultPort = null,
        )
        assertEquals(listOf("yes"), gate.outputPorts())
    }

    @Test
    fun `ToolGate has one input port named in`() {
        val gate = ToolGate(id = GateId("g"), label = "G", toolName = "SomeTool")
        assertEquals(listOf("in"), gate.inputPorts())
    }

    @Test
    fun `ToolGate has one output port named out`() {
        val gate = ToolGate(id = GateId("g"), label = "G", toolName = "SomeTool")
        assertEquals(listOf("out"), gate.outputPorts())
    }

    @Test
    fun `ReadFileGate has one input port named in`() {
        val gate = ReadFileGate(id = GateId("g"), label = "G", path = "/p", outputKey = "content")
        assertEquals(listOf("in"), gate.inputPorts())
    }

    @Test
    fun `ReadFileGate has one output port named out`() {
        val gate = ReadFileGate(id = GateId("g"), label = "G", path = "/p", outputKey = "content")
        assertEquals(listOf("out"), gate.outputPorts())
    }

    @Test
    fun `WriteFileGate has one input port named in`() {
        val gate = WriteFileGate(id = GateId("g"), label = "G", path = "/p", contentKey = "c")
        assertEquals(listOf("in"), gate.inputPorts())
    }

    @Test
    fun `WriteFileGate has one output port named out`() {
        val gate = WriteFileGate(id = GateId("g"), label = "G", path = "/p", contentKey = "c")
        assertEquals(listOf("out"), gate.outputPorts())
    }
}
