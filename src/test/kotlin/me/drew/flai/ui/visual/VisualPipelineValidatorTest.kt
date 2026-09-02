package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `valid model produces empty error list`() {
        val result = VisualPipelineValidator.validate(makeValidModel())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `missing pipeline id produces error`() {
        val result = VisualPipelineValidator.validate(makeEmptyIdModel())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "id" && it.gateId == "(pipeline)" })
    }

    @Test
    fun `missing entry gate produces error`() {
        val result = VisualPipelineValidator.validate(VisualPipelineModel())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "entry" && it.gateId == "(pipeline)" })
    }

    @Test
    fun `validate fails on duplicate gate ids`() {
        val model = VisualPipelineModel()
        model.setPipelineMetadata("p", "P")
        model.addNode(InputGate(id = GateId("dup"), label = "a"), 0, 0)
        model.addNode(OutputGate(id = GateId("dup"), label = "b"), 0, 0)
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "dup" && it.field == "id" })
    }

    @Test
    fun `blank gate id is reported against the node sequence`() {
        val model = VisualPipelineModel()
        model.setPipelineMetadata("p", "P")
        val node = model.addNode(InputGate(id = GateId(""), label = "a"), 0, 0)
        val result = VisualPipelineValidator.validateStructure(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "(gate #${node.nodeSeq})" && it.field == "id" })
    }

    @Test
    fun `edge referencing non-existent node sequence produces error`() {
        val model = makeValidModel()
        val inputNode = model.nodes[0]
        model.addEdge(VisualEdge(fromSeq = inputNode.nodeSeq, fromPort = "out", toSeq = 9999))
        val result = VisualPipelineValidator.validateStructure(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "(edge)" && it.field == "to" })
    }

    @Test
    fun `valid edge with correct fromPort produces no error`() {
        val model = makeValidModel()
        val inputNode = model.nodes[0]
        val outputNode = model.addNode(OutputGate(id = GateId("end"), label = "End"), 200, 0)
        model.addEdge(VisualEdge(fromSeq = inputNode.nodeSeq, fromPort = "out", toSeq = outputNode.nodeSeq))
        assertTrue(VisualPipelineValidator.validate(model).isValid)
    }

    @Test
    fun `validate surfaces core issues through the model path`() {
        val model = makeValidModel()
        model.addNode(
            LlmGate(
                id = GateId("llm1"),
                label = "LLM",
                promptTemplate = "Hello",
                endpointConfig = LlmEndpointConfig("", "cred", "model"),
            ),
            100, 0,
        )
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "llm1" && it.field == "endpointConfig.url" })
    }

    @Test
    fun `validate surfaces invalid fromPort from core validator`() {
        val model = makeValidModel()
        val inputNode = model.nodes[0]
        val outputNode = model.addNode(OutputGate(id = GateId("end"), label = "End"), 200, 0)
        model.addEdge(VisualEdge(fromSeq = inputNode.nodeSeq, fromPort = "nonexistent", toSeq = outputNode.nodeSeq))
        val result = VisualPipelineValidator.validate(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "fromPort" && it.gateId == "start" })
    }

    @Test
    fun `validateStructure passes for valid model`() {
        assertTrue(VisualPipelineValidator.validateStructure(makeValidModel()).isValid)
    }

    @Test
    fun `validateStructure fails on empty pipeline id`() {
        val result = VisualPipelineValidator.validateStructure(makeEmptyIdModel())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "id" && it.gateId == "(pipeline)" })
    }

    @Test
    fun `validateStructure fails on missing entry`() {
        val model = VisualPipelineModel()
        model.setPipelineMetadata("p", "P")
        val result = VisualPipelineValidator.validateStructure(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "entry" })
    }

    @Test
    fun `validateStructure fails on duplicate gate ids`() {
        val model = VisualPipelineModel()
        model.setPipelineMetadata("p", "P")
        model.addNode(InputGate(id = GateId("dup"), label = "a"), 0, 0)
        model.addNode(OutputGate(id = GateId("dup"), label = "b"), 0, 0)
        val result = VisualPipelineValidator.validateStructure(model)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.gateId == "dup" && it.field == "id" })
    }

    @Test
    fun `validateStructure ignores semantic gate errors`() {
        val model = VisualPipelineModel()
        model.setPipelineMetadata("p", "P")
        model.addNode(
            LlmGate(
                id = GateId("llm1"),
                label = "LLM",
                promptTemplate = "",
                endpointConfig = LlmEndpointConfig(url = "", credentialId = "", model = ""),
            ),
            0, 0,
        )
        assertTrue(VisualPipelineValidator.validateStructure(model).isValid)
        assertFalse(VisualPipelineValidator.validate(model).isValid)
    }
}
