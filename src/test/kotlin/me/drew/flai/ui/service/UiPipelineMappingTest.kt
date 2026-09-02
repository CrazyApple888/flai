package me.drew.flai.ui.service

import me.drew.flai.domain.model.FieldType
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputField
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.model.OutputGate
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineEdge
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.service.PipelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/** Covers [toUiPipeline] — the pure `Pipeline` → `UiPipeline` mapping including validation issues. */
class UiPipelineMappingTest {

    private val validator = PipelineValidator()
    private val filePath: Path = Path.of("/project/.flai/demo.flai.yaml")

    private fun pipelineOf(vararg gates: Gate, edges: List<PipelineEdge>): Pipeline = Pipeline(
        id = PipelineId("demo"),
        name = "Demo pipeline",
        description = "a demo",
        gates = gates.associateBy { gate -> gate.id },
        edges = edges,
        entryGateId = gates.first().id,
    )

    private fun inputGate() = InputGate(
        id = GateId("start"),
        label = "Start",
        inputSchema = listOf(
            InputField(name = "topic", type = FieldType.STRING, required = true, default = "kotlin"),
        ),
    )

    private fun validPipeline(): Pipeline = pipelineOf(
        inputGate(),
        OutputGate(id = GateId("finish"), label = "Finish"),
        edges = listOf(PipelineEdge(from = GateId("start"), to = GateId("finish"))),
    )

    /** What the visual editor writes for a freshly dropped, not yet configured LLM gate. */
    private fun unconfiguredLlmPipeline(): Pipeline = pipelineOf(
        inputGate(),
        LlmGate(
            id = GateId("ask"),
            label = "Ask",
            promptTemplate = "",
            endpointConfig = LlmEndpointConfig(url = "", model = ""),
        ),
        edges = listOf(PipelineEdge(from = GateId("start"), to = GateId("ask"))),
    )

    @Test
    fun `a valid pipeline has no validation issues`() {
        assertTrue(toUiPipeline(validPipeline(), filePath, validator).validationIssues.isEmpty())
    }

    @Test
    fun `an unconfigured llm gate produces validation issues`() {
        val uiPipeline = toUiPipeline(unconfiguredLlmPipeline(), filePath, validator)
        assertTrue(uiPipeline.validationIssues.isNotEmpty())
    }

    @Test
    fun `validation issues carry the validator messages`() {
        val uiPipeline = toUiPipeline(unconfiguredLlmPipeline(), filePath, validator)
        val expected = validator.collectIssues(unconfiguredLlmPipeline()).map { issue -> issue.message }
        assertEquals(expected, uiPipeline.validationIssues)
    }

    @Test
    fun `a pipeline with validation issues is not a parse error`() {
        assertNull(toUiPipeline(unconfiguredLlmPipeline(), filePath, validator).parseError)
    }

    @Test
    fun `pipeline details are copied over`() {
        val uiPipeline = toUiPipeline(validPipeline(), filePath, validator)
        assertEquals(PipelineId("demo"), uiPipeline.id)
        assertEquals("Demo pipeline", uiPipeline.name)
        assertEquals("a demo", uiPipeline.description)
        assertEquals(2, uiPipeline.gateCount)
        assertEquals(filePath, uiPipeline.filePath)
    }

    @Test
    fun `input specs come from the entry input gate schema`() {
        val uiPipeline = toUiPipeline(validPipeline(), filePath, validator)
        assertEquals(1, uiPipeline.inputSpecs.size)
        val spec = uiPipeline.inputSpecs.first()
        assertEquals("topic", spec.key)
        assertEquals("topic", spec.label)
        assertEquals("kotlin", spec.defaultValue)
        assertTrue(spec.required)
    }

    @Test
    fun `a pipeline whose entry gate is not an input gate has no input specs`() {
        val pipeline = pipelineOf(
            OutputGate(id = GateId("finish"), label = "Finish"),
            edges = emptyList(),
        )
        assertTrue(toUiPipeline(pipeline, filePath, validator).inputSpecs.isEmpty())
    }
}
