package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.*
import me.drew.flai.domain.service.ExecutionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoroutinePipelineExecutorFaultTolerantTest {

    /** Always returns Failure for the gate type it is constructed with. */
    private class FailingExecutor<G : Gate>(
        private val type: Class<G>,
        private val message: String = "boom",
    ) : GateExecutor<G> {
        override fun canHandle(gate: Gate) = type.isInstance(gate)

        override suspend fun execute(gate: G, context: ExecutionContext): GateResult =
            GateResult.Failure(IllegalStateException(message), message = message)
    }

    /** Succeeds for the gate with the given id, regardless of type. */
    private class PassthroughForId(private val id: String) : GateExecutor<Gate> {
        override fun canHandle(gate: Gate) = gate.id.value == id
        override suspend fun execute(gate: Gate, context: ExecutionContext): GateResult =
            GateResult.Success()
    }

    private fun executorOf(vararg executors: GateExecutor<*>) =
        CoroutinePipelineExecutor(executors.toList())

    @Test
    fun `non-fault-tolerant gate fails aborts run and skips downstream`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to InputGate(GateId("a"), "A", faultTolerant = false),
                GateId("b") to OutputGate(GateId("b"), "B"),
            ),
            edges = listOf(PipelineEdge(GateId("a"), to = GateId("b"))),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(
            FailingExecutor(InputGate::class.java),
            DefaultOutputGateExecutor(),
        )

        val events = executor.execute(pipeline).toList()

        assertTrue(events.last() is ExecutionEvent.PipelineFailed)
        assertFalse(events.any { it is ExecutionEvent.GateStarted && it.gateId == "b" })
    }

    @Test
    fun `fault-tolerant gate fails continues along out edge to completion`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to InputGate(GateId("a"), "A", faultTolerant = true),
                GateId("b") to OutputGate(GateId("b"), "B"),
            ),
            edges = listOf(PipelineEdge(GateId("a"), to = GateId("b"))),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(
            FailingExecutor(InputGate::class.java),
            DefaultOutputGateExecutor(),
        )

        val events = executor.execute(pipeline).toList()

        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
        assertFalse(events.any { it is ExecutionEvent.PipelineFailed })
        assertTrue(events.any { it is ExecutionEvent.GateStarted && it.gateId == "b" })
    }

    @Test
    fun `downstream gate sees pre-failure context with no outputs from failed gate`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to BashGate(GateId("a"), "A", command = "x", faultTolerant = true),
                GateId("b") to OutputGate(GateId("b"), "B"),
            ),
            edges = listOf(PipelineEdge(GateId("a"), to = GateId("b"))),
            entryGateId = GateId("a"),
        )
        var seen: Map<String, Any?> = emptyMap()
        val capturing = object : GateExecutor<OutputGate> {
            override fun canHandle(gate: Gate) = gate is OutputGate
            override suspend fun execute(gate: OutputGate, context: ExecutionContext): GateResult {
                seen = context.snapshot()
                return GateResult.Success()
            }
        }
        val executor = executorOf(FailingExecutor(BashGate::class.java), capturing)

        executor.execute(pipeline).toList()

        assertFalse(seen.containsKey("stdout"))
        assertNull(seen["stdout"])
    }

    @Test
    fun `tolerated failure trace entry has TOLERATED_FAILURE status and reason in message`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("a") to InputGate(GateId("a"), "A", faultTolerant = true)),
            edges = emptyList(),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(FailingExecutor(InputGate::class.java, message = "disk full"))

        val events = executor.execute(pipeline).toList()
        val entry = events.filterIsInstance<ExecutionEvent.GateCompleted>().single().entry

        assertEquals(TraceStatus.TOLERATED_FAILURE, entry.status)
        assertTrue(entry.message!!.contains("disk full"))
        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }

    @Test
    fun `each gate type when fault tolerant continues per FR-4`() = runBlocking {
        val gateTypes: List<Pair<Gate, Class<out Gate>>> = listOf(
            InputGate(GateId("a"), "A", faultTolerant = true) to InputGate::class.java,
            OutputGate(GateId("a"), "A", faultTolerant = true) to OutputGate::class.java,
            LlmGate(
                GateId("a"), "A", promptTemplate = "x",
                endpointConfig = LlmEndpointConfig("u", "c", "m"), faultTolerant = true,
            ) to LlmGate::class.java,
            ToolGate(GateId("a"), "A", toolName = "t", faultTolerant = true) to ToolGate::class.java,
            BashGate(GateId("a"), "A", command = "x", faultTolerant = true) to BashGate::class.java,
            ReadFileGate(GateId("a"), "A", path = "p", faultTolerant = true) to ReadFileGate::class.java,
            WriteFileGate(GateId("a"), "A", path = "p", contentKey = "c", faultTolerant = true) to WriteFileGate::class.java,
        )
        for ((gate, type) in gateTypes) {
            val pipeline = Pipeline(
                id = PipelineId("p"),
                name = "P",
                gates = mapOf(GateId("a") to gate, GateId("b") to OutputGate(GateId("b"), "B")),
                edges = listOf(PipelineEdge(GateId("a"), to = GateId("b"))),
                entryGateId = GateId("a"),
            )
            val failing = object : GateExecutor<Gate> {
                override fun canHandle(gate: Gate) = gate.id.value == "a" && type.isInstance(gate)
                override suspend fun execute(gate: Gate, context: ExecutionContext): GateResult =
                    GateResult.Failure(IllegalStateException("boom"), message = "boom")
            }
            val executor = executorOf(failing, PassthroughForId("b"))

            val events = executor.execute(pipeline).toList()

            assertTrue(
                "Type ${type.simpleName} should continue to downstream",
                events.any { it is ExecutionEvent.GateStarted && it.gateId == "b" },
            )
            assertTrue(
                "Type ${type.simpleName} should complete",
                events.last() is ExecutionEvent.PipelineCompleted,
            )
        }
    }

    @Test
    fun `fault-tolerant logic gate with null defaultPort fails and run ends normally`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to LogicGate(
                    GateId("a"),
                    "A",
                    branches = listOf(Branch("yes", BranchCondition.Comparison("missing", ComparisonOp.EQ, "x"))),
                    defaultPort = null,
                    faultTolerant = true,
                ),
                GateId("b") to OutputGate(GateId("b"), "B"),
            ),
            edges = emptyList(),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(DefaultLogicGateExecutor(), DefaultOutputGateExecutor())

        val events = executor.execute(pipeline).toList()
        val entry = events.filterIsInstance<ExecutionEvent.GateCompleted>().single().entry

        assertEquals(TraceStatus.TOLERATED_FAILURE, entry.status)
        assertFalse(events.any { it is ExecutionEvent.GateStarted && it.gateId == "b" })
        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }

    @Test
    fun `fault-tolerant logic gate failure continues along defaultPort`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to LogicGate(
                    GateId("a"),
                    "A",
                    branches = emptyList(),
                    defaultPort = "fallback",
                    faultTolerant = true,
                ),
                GateId("b") to OutputGate(GateId("b"), "B"),
            ),
            edges = listOf(PipelineEdge(GateId("a"), fromPort = "fallback", to = GateId("b"))),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(
            FailingExecutor(LogicGate::class.java),
            DefaultOutputGateExecutor(),
        )

        val events = executor.execute(pipeline).toList()

        assertTrue(events.any { it is ExecutionEvent.GateStarted && it.gateId == "b" })
        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }

    @Test
    fun `logic gate evaluating false branch is recorded SUCCESS unaffected by flag`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to LogicGate(
                    GateId("a"),
                    "A",
                    branches = listOf(Branch("yes", BranchCondition.Comparison("missing", ComparisonOp.EQ, "x"))),
                    defaultPort = "default",
                    faultTolerant = true,
                ),
                GateId("b") to OutputGate(GateId("b"), "B"),
            ),
            edges = listOf(PipelineEdge(GateId("a"), fromPort = "default", to = GateId("b"))),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(DefaultLogicGateExecutor(), DefaultOutputGateExecutor())

        val events = executor.execute(pipeline).toList()
        val entry = events.filterIsInstance<ExecutionEvent.GateCompleted>().first().entry

        assertEquals(TraceStatus.SUCCESS, entry.status)
        assertTrue(events.any { it is ExecutionEvent.GateStarted && it.gateId == "b" })
    }

    @Test
    fun `entry gate fault tolerant and fails continues along out edge`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("entry") to InputGate(GateId("entry"), "Entry", faultTolerant = true),
                GateId("next") to OutputGate(GateId("next"), "Next"),
            ),
            edges = listOf(PipelineEdge(GateId("entry"), to = GateId("next"))),
            entryGateId = GateId("entry"),
        )
        val executor = executorOf(FailingExecutor(InputGate::class.java), DefaultOutputGateExecutor())

        val events = executor.execute(pipeline).toList()

        assertTrue(events.any { it is ExecutionEvent.GateStarted && it.gateId == "next" })
        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }

    @Test
    fun `fault-tolerant gate with no outgoing edge ends run normally`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("a") to InputGate(GateId("a"), "A", faultTolerant = true)),
            edges = emptyList(),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(FailingExecutor(InputGate::class.java))

        val events = executor.execute(pipeline).toList()

        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }

    @Test
    fun `two fault-tolerant failures produce two distinct tolerated entries`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to InputGate(GateId("a"), "A", faultTolerant = true),
                GateId("b") to BashGate(GateId("b"), "B", command = "x", faultTolerant = true),
                GateId("c") to OutputGate(GateId("c"), "C"),
            ),
            edges = listOf(
                PipelineEdge(GateId("a"), to = GateId("b")),
                PipelineEdge(GateId("b"), to = GateId("c")),
            ),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(
            FailingExecutor(InputGate::class.java),
            FailingExecutor(BashGate::class.java),
            DefaultOutputGateExecutor(),
        )

        val events = executor.execute(pipeline).toList()
        val tolerated = events.filterIsInstance<ExecutionEvent.GateCompleted>()
            .filter { it.entry.status == TraceStatus.TOLERATED_FAILURE }

        assertEquals(2, tolerated.size)
        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }

    @Test
    fun `non-fault-tolerant gate downstream of tolerated failure still aborts`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("a") to InputGate(GateId("a"), "A", faultTolerant = true),
                GateId("b") to BashGate(GateId("b"), "B", command = "x", faultTolerant = false),
                GateId("c") to OutputGate(GateId("c"), "C"),
            ),
            edges = listOf(
                PipelineEdge(GateId("a"), to = GateId("b")),
                PipelineEdge(GateId("b"), to = GateId("c")),
            ),
            entryGateId = GateId("a"),
        )
        val executor = executorOf(
            FailingExecutor(InputGate::class.java),
            FailingExecutor(BashGate::class.java),
            DefaultOutputGateExecutor(),
        )

        val events = executor.execute(pipeline).toList()

        assertTrue(events.last() is ExecutionEvent.PipelineFailed)
        assertFalse(events.any { it is ExecutionEvent.GateStarted && it.gateId == "c" })
    }

    @Test
    fun `fault-tolerant gate failing with blank reason still recorded as tolerated`() = runBlocking {
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("a") to InputGate(GateId("a"), "A", faultTolerant = true)),
            edges = emptyList(),
            entryGateId = GateId("a"),
        )
        val blankFailing = object : GateExecutor<InputGate> {
            override fun canHandle(gate: Gate) = gate is InputGate
            override suspend fun execute(gate: InputGate, context: ExecutionContext): GateResult =
                GateResult.Failure(IllegalStateException(), message = "")
        }
        val executor = executorOf(blankFailing)

        val events = executor.execute(pipeline).toList()
        val entry = events.filterIsInstance<ExecutionEvent.GateCompleted>().single().entry

        assertEquals(TraceStatus.TOLERATED_FAILURE, entry.status)
        assertTrue(events.last() is ExecutionEvent.PipelineCompleted)
    }
}
