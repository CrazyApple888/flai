package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlPipelineSerializerTest {

    private val parser = YamlPipelineParser()
    private val serializer = YamlPipelineSerializer()

    private fun roundTrip(pipeline: Pipeline): Pipeline {
        val yaml = serializer.serialize(pipeline)
        return parser.parse(yaml)
    }

    private fun minimalPipeline(
        gates: Map<GateId, Gate>,
        edges: List<PipelineEdge> = emptyList(),
        entryGateId: GateId = gates.keys.first(),
    ) = Pipeline(
        id = PipelineId("test-pipeline"),
        name = "Test Pipeline",
        description = "",
        gates = gates,
        edges = edges,
        entryGateId = entryGateId,
    )

    @Test
    fun `round-trip InputGate with inputSchema`() {
        val gate = InputGate(
            id = GateId("start"),
            label = "Start",
            inputSchema = listOf(
                InputField("query", FieldType.STRING, required = true),
                InputField("count", FieldType.NUMBER, required = false, default = "10"),
            ),
        )
        val pipeline = minimalPipeline(mapOf(GateId("start") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("start")] as InputGate
        assertEquals(gate.inputSchema, parsed.inputSchema)
    }

    @Test
    fun `round-trip OutputGate with outputMapping`() {
        val gate = OutputGate(
            id = GateId("end"),
            label = "End",
            outputMapping = mapOf("result" to "ctx_result"),
        )
        val pipeline = minimalPipeline(mapOf(GateId("end") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("end")] as OutputGate
        assertEquals(gate.outputMapping, parsed.outputMapping)
    }

    @Test
    fun `round-trip LlmGate with single-line promptTemplate`() {
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Answer this: {{question}}",
            endpointConfig = LlmEndpointConfig(
                url = "https://api.anthropic.com/v1/messages",
                credentialId = "my-key",
                model = "claude-sonnet-4-6",
            ),
        )
        val pipeline = minimalPipeline(mapOf(GateId("llm1") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("llm1")] as LlmGate
        assertEquals(gate.promptTemplate, parsed.promptTemplate)
        assertEquals(gate.endpointConfig, parsed.endpointConfig)
    }

    @Test
    fun `round-trip LlmGate with multi-line promptTemplate preserved as literal block`() {
        val multilinePrompt = "You are an assistant.\n\nAnswer the question:\n{{question}}"
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = multilinePrompt,
            endpointConfig = LlmEndpointConfig(
                url = "https://api.anthropic.com/v1/messages",
                credentialId = "my-key",
                model = "claude-sonnet-4-6",
            ),
        )
        val pipeline = minimalPipeline(mapOf(GateId("llm1") to gate))
        val yaml = serializer.serialize(pipeline)
        assertTrue("YAML should contain literal block indicator '|'", yaml.contains("promptTemplate: |"))
        val result = parser.parse(yaml)
        val parsed = result.gates[GateId("llm1")] as LlmGate
        assertEquals(multilinePrompt, parsed.promptTemplate.trimEnd('\n'))
    }

    @Test
    fun `round-trip LlmGate with skills and inputMapping and custom outputMapping`() {
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Do it",
            skills = listOf(".flai/skills/persona.md"),
            inputMapping = mapOf("q" to "query"),
            outputMapping = mapOf("ans" to "answer"),
            endpointConfig = LlmEndpointConfig(
                url = "https://api.example.com",
                credentialId = "cred",
                model = "model-x",
                params = mapOf("temperature" to 0.7),
            ),
        )
        val pipeline = minimalPipeline(mapOf(GateId("llm1") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("llm1")] as LlmGate
        assertEquals(gate.skills, parsed.skills)
        assertEquals(gate.inputMapping, parsed.inputMapping)
        assertEquals(gate.outputMapping, parsed.outputMapping)
        assertEquals(gate.endpointConfig.params["temperature"].toString(), parsed.endpointConfig.params["temperature"].toString())
    }

    @Test
    fun `round-trip LlmGate default outputMapping omitted in YAML`() {
        val gate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Do it",
            outputMapping = mapOf("response" to "response"),
            endpointConfig = LlmEndpointConfig(
                url = "https://api.example.com",
                credentialId = "cred",
                model = "model",
            ),
        )
        val pipeline = minimalPipeline(mapOf(GateId("llm1") to gate))
        val yaml = serializer.serialize(pipeline)
        // The default outputMapping should not be written to YAML
        assertTrue("Default outputMapping should not appear in YAML", !yaml.contains("outputMapping"))
        val result = parser.parse(yaml)
        val parsed = result.gates[GateId("llm1")] as LlmGate
        assertEquals(gate.outputMapping, parsed.outputMapping)
    }

    @Test
    fun `round-trip LogicGate with comparison branch`() {
        val gate = LogicGate(
            id = GateId("branch"),
            label = "Branch",
            branches = listOf(
                Branch(
                    port = "high",
                    condition = BranchCondition.Comparison("score", ComparisonOp.GTE, "80"),
                ),
            ),
            defaultPort = "default",
        )
        val pipeline = minimalPipeline(mapOf(GateId("branch") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("branch")] as LogicGate
        assertEquals(gate.branches, parsed.branches)
        assertEquals(gate.defaultPort, parsed.defaultPort)
    }

    @Test
    fun `round-trip LogicGate with switch branch`() {
        val gate = LogicGate(
            id = GateId("branch"),
            label = "Branch",
            branches = listOf(
                Branch(
                    port = "case-a",
                    condition = BranchCondition.SwitchCase("env", listOf("prod", "staging")),
                ),
            ),
            defaultPort = "other",
        )
        val pipeline = minimalPipeline(mapOf(GateId("branch") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("branch")] as LogicGate
        assertEquals(gate.branches, parsed.branches)
    }

    @Test
    fun `round-trip LogicGate with always branch`() {
        val gate = LogicGate(
            id = GateId("branch"),
            label = "Branch",
            branches = listOf(
                Branch(port = "always-out", condition = BranchCondition.Always),
            ),
            defaultPort = "default",
        )
        val pipeline = minimalPipeline(mapOf(GateId("branch") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("branch")] as LogicGate
        assertEquals("always-out", parsed.branches[0].port)
        assertEquals(BranchCondition.Always, parsed.branches[0].condition)
    }

    @Test
    fun `round-trip ToolGate`() {
        val gate = ToolGate(
            id = GateId("tool1"),
            label = "Tool",
            toolName = "FileReadTool",
            inputMapping = mapOf("p" to "path"),
            outputMapping = mapOf("out" to "content"),
        )
        val pipeline = minimalPipeline(mapOf(GateId("tool1") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("tool1")] as ToolGate
        assertEquals(gate.toolName, parsed.toolName)
        assertEquals(gate.inputMapping, parsed.inputMapping)
        assertEquals(gate.outputMapping, parsed.outputMapping)
    }

    @Test
    fun `round-trip ReadFileGate with default outputKey omitted`() {
        val gate = ReadFileGate(
            id = GateId("read1"),
            label = "Read",
            path = "/some/file.txt",
            outputKey = "content",
        )
        val pipeline = minimalPipeline(mapOf(GateId("read1") to gate))
        val yaml = serializer.serialize(pipeline)
        assertTrue("Default outputKey should not appear in YAML", !yaml.contains("outputKey"))
        val result = parser.parse(yaml)
        val parsed = result.gates[GateId("read1")] as ReadFileGate
        assertEquals(gate.path, parsed.path)
        assertEquals(gate.outputKey, parsed.outputKey)
    }

    @Test
    fun `round-trip ReadFileGate with non-default outputKey`() {
        val gate = ReadFileGate(
            id = GateId("read1"),
            label = "Read",
            path = "/some/file.txt",
            outputKey = "file_body",
        )
        val pipeline = minimalPipeline(mapOf(GateId("read1") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("read1")] as ReadFileGate
        assertEquals("file_body", parsed.outputKey)
    }

    @Test
    fun `round-trip WriteFileGate with default mode omitted`() {
        val gate = WriteFileGate(
            id = GateId("write1"),
            label = "Write",
            path = "/out/file.txt",
            contentKey = "content",
            mode = WriteMode.OVERWRITE,
        )
        val pipeline = minimalPipeline(mapOf(GateId("write1") to gate))
        val yaml = serializer.serialize(pipeline)
        assertTrue("Default mode should not appear in YAML", !yaml.contains("mode:"))
        val result = parser.parse(yaml)
        val parsed = result.gates[GateId("write1")] as WriteFileGate
        assertEquals(WriteMode.OVERWRITE, parsed.mode)
    }

    @Test
    fun `round-trip WriteFileGate with append mode`() {
        val gate = WriteFileGate(
            id = GateId("write1"),
            label = "Write",
            path = "/out/file.txt",
            contentKey = "content",
            mode = WriteMode.APPEND,
        )
        val pipeline = minimalPipeline(mapOf(GateId("write1") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("write1")] as WriteFileGate
        assertEquals(WriteMode.APPEND, parsed.mode)
    }

    @Test
    fun `round-trip WriteFileGate with fail-if-exists mode`() {
        val gate = WriteFileGate(
            id = GateId("write1"),
            label = "Write",
            path = "/out/file.txt",
            contentKey = "content",
            mode = WriteMode.FAIL_IF_EXISTS,
        )
        val pipeline = minimalPipeline(mapOf(GateId("write1") to gate))
        val result = roundTrip(pipeline)
        val parsed = result.gates[GateId("write1")] as WriteFileGate
        assertEquals(WriteMode.FAIL_IF_EXISTS, parsed.mode)
    }

    @Test
    fun `edge with default ports omits fromPort and toPort`() {
        val g1 = InputGate(id = GateId("g1"), label = "G1")
        val g2 = OutputGate(id = GateId("g2"), label = "G2")
        val edge = PipelineEdge(from = GateId("g1"), to = GateId("g2"))
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("g1") to g1, GateId("g2") to g2),
            edges = listOf(edge),
            entryGateId = GateId("g1"),
        )
        val yaml = serializer.serialize(pipeline)
        assertTrue("fromPort should not appear for default 'out'", !yaml.contains("fromPort"))
        assertTrue("toPort should not appear for default 'in'", !yaml.contains("toPort"))
        val result = parser.parse(yaml)
        assertEquals("out", result.edges[0].fromPort)
        assertEquals("in", result.edges[0].toPort)
    }

    @Test
    fun `edge with non-default ports includes fromPort and toPort`() {
        val g1 = LogicGate(
            id = GateId("g1"),
            label = "G1",
            branches = listOf(Branch("yes", BranchCondition.Always)),
            defaultPort = "no",
        )
        val g2 = OutputGate(id = GateId("g2"), label = "G2")
        val edge = PipelineEdge(from = GateId("g1"), fromPort = "yes", to = GateId("g2"), toPort = "in")
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("g1") to g1, GateId("g2") to g2),
            edges = listOf(edge),
            entryGateId = GateId("g1"),
        )
        val yaml = serializer.serialize(pipeline)
        assertTrue("fromPort should appear in YAML", yaml.contains("fromPort:"))
        val result = parser.parse(yaml)
        assertEquals("yes", result.edges[0].fromPort)
    }

    @Test
    fun `full round-trip with all 7 gate types`() {
        val inputGate = InputGate(
            id = GateId("start"),
            label = "Start",
            inputSchema = listOf(InputField("q", FieldType.STRING)),
        )
        val llmGate = LlmGate(
            id = GateId("llm1"),
            label = "LLM",
            promptTemplate = "Answer: {{q}}",
            endpointConfig = LlmEndpointConfig("https://api.example.com", "key", "model"),
        )
        val logicGate = LogicGate(
            id = GateId("logic1"),
            label = "Logic",
            branches = listOf(Branch("yes", BranchCondition.Comparison("score", ComparisonOp.GTE, "5"))),
            defaultPort = "no",
        )
        val toolGate = ToolGate(
            id = GateId("tool1"),
            label = "Tool",
            toolName = "FileReadTool",
        )
        val readGate = ReadFileGate(
            id = GateId("read1"),
            label = "Read",
            path = "/data.txt",
        )
        val writeGate = WriteFileGate(
            id = GateId("write1"),
            label = "Write",
            path = "/out.txt",
            contentKey = "content",
        )
        val outputGate = OutputGate(
            id = GateId("end"),
            label = "End",
        )
        val gates = mapOf(
            GateId("start") to inputGate,
            GateId("llm1") to llmGate,
            GateId("logic1") to logicGate,
            GateId("tool1") to toolGate,
            GateId("read1") to readGate,
            GateId("write1") to writeGate,
            GateId("end") to outputGate,
        )
        val pipeline = Pipeline(
            id = PipelineId("all-gates"),
            name = "All Gates",
            description = "A pipeline with all gate types",
            gates = gates,
            edges = emptyList(),
            entryGateId = GateId("start"),
        )
        val result = roundTrip(pipeline)
        assertEquals(pipeline.id, result.id)
        assertEquals(pipeline.name, result.name)
        assertEquals(pipeline.description, result.description)
        assertEquals(7, result.gates.size)
        assertTrue(result.gates[GateId("start")] is InputGate)
        assertTrue(result.gates[GateId("llm1")] is LlmGate)
        assertTrue(result.gates[GateId("logic1")] is LogicGate)
        assertTrue(result.gates[GateId("tool1")] is ToolGate)
        assertTrue(result.gates[GateId("read1")] is ReadFileGate)
        assertTrue(result.gates[GateId("write1")] is WriteFileGate)
        assertTrue(result.gates[GateId("end")] is OutputGate)
    }

    @Test
    fun `description omitted when empty`() {
        val gate = InputGate(id = GateId("g1"), label = "G1")
        val pipeline = minimalPipeline(mapOf(GateId("g1") to gate))
        val yaml = serializer.serialize(pipeline)
        assertTrue("Empty description should not appear in YAML", !yaml.contains("description:"))
    }

    @Test
    fun `description included when non-empty`() {
        val gate = InputGate(id = GateId("g1"), label = "G1")
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            description = "A description",
            gates = mapOf(GateId("g1") to gate),
            edges = emptyList(),
            entryGateId = GateId("g1"),
        )
        val yaml = serializer.serialize(pipeline)
        assertTrue("Non-empty description should appear in YAML", yaml.contains("description: A description"))
        val result = parser.parse(yaml)
        assertEquals("A description", result.description)
    }
}
