package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VisualPipelineValidatorTest {

    private fun makeValidModel(): VisualPipelineModel {
        val gate = InputGate(id = GateId("start"), label = "Start")
        val pipeline = Pipeline(
            id = PipelineId("test"),
            name = "Test",
            gates = mapOf(GateId("start") to gate),
            edges = emptyList(),
            entryGateId = GateId("start"),
        )
        return VisualPipelineModel.fromPipeline(pipeline)
    }

    private fun makeEmptyIdModel(): VisualPipelineModel {
        val gate = InputGate(id = GateId("start"), label = "Start")
        val pipeline = Pipeline(
            id = PipelineId(""),
            name = "Test",
            gates = mapOf(GateId("start") to gate),
            edges = emptyList(),
            entryGateId = GateId("start"),
        )
        return VisualPipelineModel.fromPipeline(pipeline)
    }

    private fun makeNoEntryModel(): VisualPipelineModel {
        // Build a model with a valid pipeline then clear the entry
        val gate = InputGate(id = GateId("start"), label = "Start")
        val model = VisualPipelineModel()
        model.addNode(gate, 0, 0)
        // Don't set entry — entryNodeSeq defaults to -1
        return model
    }

    @Test
    fun `valid model produces empty error list`() {
        val model = makeValidModel()
        val result = VisualPipelineValidator.validate(model)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `missing pipeline id produces error`() {
        val model = makeEmptyIdModel()
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "id" && it.gateId == "(pipeline)" })
    }

    @Test
    fun `missing entry gate produces error`() {
        val model = makeNoEntryModel()
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "entry" && it.gateId == "(pipeline)" })
    }

    @Test
    fun `LlmGate missing promptTemplate produces error`() {
        val model = makeValidModel()
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "",
            endpointConfig = LlmEndpointConfig("https://url", "cred", "model"),
        )
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "llm1" && it.field == "promptTemplate" })
    }

    @Test
    fun `LlmGate missing url produces error`() {
        val model = makeValidModel()
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Hello",
            endpointConfig = LlmEndpointConfig("", "cred", "model"),
        )
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "llm1" && it.field == "endpointConfig.url" })
    }

    @Test
    fun `LlmGate missing credentialId produces error`() {
        val model = makeValidModel()
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Hello",
            endpointConfig = LlmEndpointConfig("https://url", "", "model"),
        )
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "llm1" && it.field == "endpointConfig.credentialId" })
    }

    @Test
    fun `LlmGate missing model produces error`() {
        val model = makeValidModel()
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Hello",
            endpointConfig = LlmEndpointConfig("https://url", "cred", ""),
        )
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "llm1" && it.field == "endpointConfig.model" })
    }

    @Test
    fun `LogicGate missing defaultPort produces error`() {
        val model = makeValidModel()
        val gate = LogicGate(
            id = GateId("logic1"),
            label = "Logic",
            branches = listOf(Branch("yes", BranchCondition.Always)),
            defaultPort = null,
        )
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "logic1" && it.field == "defaultPort" })
    }

    @Test
    fun `LogicGate branch with empty port produces error`() {
        val model = makeValidModel()
        val gate = LogicGate(
            id = GateId("logic1"),
            label = "Logic",
            branches = listOf(Branch("", BranchCondition.Always)),
            defaultPort = "default",
        )
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "logic1" && it.field == "branch.port" })
    }

    @Test
    fun `ToolGate missing toolName produces error`() {
        val model = makeValidModel()
        val gate = ToolGate(id = GateId("tool1"), label = "Tool", toolName = "")
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "tool1" && it.field == "toolName" })
    }

    @Test
    fun `ReadFileGate missing path produces error`() {
        val model = makeValidModel()
        val gate = ReadFileGate(id = GateId("read1"), label = "Read", path = "", outputKey = "content")
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "read1" && it.field == "path" })
    }

    @Test
    fun `ReadFileGate missing outputKey produces error`() {
        val model = makeValidModel()
        val gate = ReadFileGate(id = GateId("read1"), label = "Read", path = "/file.txt", outputKey = "")
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "read1" && it.field == "outputKey" })
    }

    @Test
    fun `WriteFileGate missing path produces error`() {
        val model = makeValidModel()
        val gate = WriteFileGate(id = GateId("write1"), label = "Write", path = "", contentKey = "content")
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "write1" && it.field == "path" })
    }

    @Test
    fun `WriteFileGate missing contentKey produces error`() {
        val model = makeValidModel()
        val gate = WriteFileGate(id = GateId("write1"), label = "Write", path = "/out.txt", contentKey = "")
        model.addNode(gate, 100, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "write1" && it.field == "contentKey" })
    }

    @Test
    fun `EC-10 edge referencing non-existent gate produces error`() {
        val model = makeValidModel()
        val inputNode = model.nodes[0]
        // Add an edge via addEdge to a nonexistent seq
        model.addEdge(VisualEdge(fromSeq = inputNode.nodeSeq, fromPort = "out", toSeq = 9999))
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "to" })
    }

    @Test
    fun `EC-11 edge with invalid fromPort produces error`() {
        val model = makeValidModel()
        val inputNode = model.nodes[0]
        val outputGate = OutputGate(id = GateId("end"), label = "End")
        val outputNode = model.addNode(outputGate, 200, 0)
        // InputGate output ports = ["out"], so "nonexistent" is invalid
        model.addEdge(VisualEdge(fromSeq = inputNode.nodeSeq, fromPort = "nonexistent", toSeq = outputNode.nodeSeq))
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "fromPort" && it.gateId == "start" })
    }

    @Test
    fun `valid edge with correct fromPort produces no error`() {
        val model = makeValidModel()
        val inputNode = model.nodes[0]
        val outputGate = OutputGate(id = GateId("end"), label = "End")
        val outputNode = model.addNode(outputGate, 200, 0)
        model.addEdge(VisualEdge(fromSeq = inputNode.nodeSeq, fromPort = "out", toSeq = outputNode.nodeSeq))
        val result = VisualPipelineValidator.validate(model)
        assertTrue(result.isValid)
    }
}
