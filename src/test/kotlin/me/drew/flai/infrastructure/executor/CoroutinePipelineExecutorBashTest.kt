package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.OutputGate
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineEdge
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.service.ExecutionEvent
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoroutinePipelineExecutorBashTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var executor: CoroutinePipelineExecutor

    @Before
    fun setUp() {
        executor = CoroutinePipelineExecutor(
            listOf(
                DefaultInputGateExecutor(),
                DefaultBashGateExecutor(tempFolder.root.toPath().toRealPath().toString(), SimpleTemplateRenderer()),
                DefaultOutputGateExecutor(),
            )
        )
    }

    @Test
    fun `bash output mapping reaches final output gate`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("start") to InputGate(GateId("start"), "Start"),
                GateId("run") to BashGate(
                    id = GateId("run"),
                    label = "Run",
                    command = "printf hello",
                    outputMapping = mapOf("stdout" to "command_output"),
                ),
                GateId("end") to OutputGate(
                    id = GateId("end"),
                    label = "End",
                    outputMapping = mapOf("result" to "command_output"),
                ),
            ),
            edges = listOf(
                PipelineEdge(GateId("start"), to = GateId("run")),
                PipelineEdge(GateId("run"), to = GateId("end")),
            ),
            entryGateId = GateId("start"),
        )

        val events = executor.execute(pipeline).toList()
        val completed = events.last() as ExecutionEvent.PipelineCompleted

        assertEquals("hello", completed.outputs["result"])
    }

    @Test
    fun `non-zero bash gate with failOnNonZeroExit true halts pipeline`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("start") to InputGate(GateId("start"), "Start"),
                GateId("run") to BashGate(
                    id = GateId("run"),
                    label = "Run",
                    command = "exit 2",
                ),
                GateId("end") to OutputGate(
                    id = GateId("end"),
                    label = "End",
                    outputMapping = mapOf("result" to "stdout"),
                ),
            ),
            edges = listOf(
                PipelineEdge(GateId("start"), to = GateId("run")),
                PipelineEdge(GateId("run"), to = GateId("end")),
            ),
            entryGateId = GateId("start"),
        )

        val events = executor.execute(pipeline).toList()

        assertTrue(events.last() is ExecutionEvent.PipelineFailed)
        assertFalse(events.any { it is ExecutionEvent.GateStarted && it.gateId == "end" })
    }
}
