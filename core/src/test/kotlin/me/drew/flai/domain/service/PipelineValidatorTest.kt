package me.drew.flai.domain.service

import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.Branch
import me.drew.flai.domain.model.BranchCondition
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.model.LogicGate
import me.drew.flai.domain.model.OutputGate
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineEdge
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.model.ReadFileGate
import me.drew.flai.domain.model.ToolGate
import me.drew.flai.domain.model.WriteFileGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PipelineValidatorTest {

    private val validator = PipelineValidator()

    private fun pipeline(
        vararg gates: Gate,
        edges: List<PipelineEdge> = emptyList(),
        id: String = "test",
        entry: String = gates.first().id.value,
    ): Pipeline = Pipeline(
        id = PipelineId(id),
        name = "Test",
        gates = gates.associateBy { it.id },
        edges = edges,
        entryGateId = GateId(entry),
    )

    private fun start(): InputGate = InputGate(id = GateId("start"), label = "Start")

    private fun end(): OutputGate = OutputGate(id = GateId("end"), label = "End")

    private fun llm(
        id: String = "llm1",
        promptTemplate: String = "Hello",
        url: String = "https://url",
        credentialId: String = "cred",
        model: String = "model",
        apiKeyVar: String? = null,
        maxToolRounds: Int = 8,
        tools: List<String> = emptyList(),
    ): LlmGate = LlmGate(
        id = GateId(id),
        label = "LLM",
        promptTemplate = promptTemplate,
        endpointConfig = LlmEndpointConfig(url = url, credentialId = credentialId, model = model, apiKeyVar = apiKeyVar),
        tools = tools,
        maxToolRounds = maxToolRounds,
    )

    private fun logic(
        id: String = "logic1",
        branches: List<Branch> = listOf(Branch("yes", BranchCondition.Always)),
        defaultPort: String? = "default",
    ): LogicGate = LogicGate(id = GateId(id), label = "Logic", branches = branches, defaultPort = defaultPort)

    private fun issues(pipeline: Pipeline): List<ValidationIssue> = validator.collectIssues(pipeline)

    private fun assertIssue(issues: List<ValidationIssue>, scope: IssueScope, gateId: String?, field: String) {
        assertTrue(
            "expected issue scope=$scope gateId=$gateId field=$field in $issues",
            issues.any { it.scope == scope && it.gateId == gateId && it.field == field },
        )
    }

    private fun assertGateIssue(issues: List<ValidationIssue>, gateId: String, field: String) {
        assertIssue(issues, IssueScope.GATE, gateId, field)
    }

    private fun assertMessageMentions(issues: List<ValidationIssue>, field: String, fragment: String) {
        val issue = issues.first { it.field == field }
        assertTrue("expected '$fragment' in '${issue.message}'", issue.message.contains(fragment))
    }

    @Test
    fun `valid pipeline has no issues`() {
        val p = pipeline(
            start(),
            llm(),
            end(),
            edges = listOf(
                PipelineEdge(from = GateId("start"), to = GateId("llm1")),
                PipelineEdge(from = GateId("llm1"), to = GateId("end")),
            ),
        )
        assertTrue(issues(p).isEmpty())
        validator.validate(p)
    }

    @Test
    fun `blank pipeline id produces PIPELINE issue`() {
        val result = issues(pipeline(start(), id = "  "))
        assertIssue(result, IssueScope.PIPELINE, null, "id")
    }

    @Test
    fun `missing entry gate produces PIPELINE issue`() {
        val result = issues(pipeline(start(), entry = "nope"))
        assertIssue(result, IssueScope.PIPELINE, null, "entry")
        assertMessageMentions(result, "entry", "nope")
    }

    @Test
    fun `gate id with invalid characters produces GATE issue`() {
        val result = issues(pipeline(InputGate(id = GateId("bad id!"), label = "x")))
        assertGateIssue(result, "bad id!", "id")
        assertMessageMentions(result, "id", "bad id!")
    }

    @Test
    fun `blank gate id produces GATE issue`() {
        val result = issues(pipeline(InputGate(id = GateId(""), label = "x")))
        assertGateIssue(result, "", "id")
    }

    @Test
    fun `gate ids with letters digits underscore dot and dash are accepted`() {
        val gate = InputGate(id = GateId("Ab9_.-z"), label = "x")
        assertTrue(issues(pipeline(gate)).isEmpty())
    }

    @Test
    fun `LlmGate blank promptTemplate`() {
        assertGateIssue(issues(pipeline(start(), llm(promptTemplate = "   "))), "llm1", "promptTemplate")
    }

    @Test
    fun `LlmGate blank url`() {
        assertGateIssue(issues(pipeline(start(), llm(url = ""))), "llm1", "endpointConfig.url")
    }

    @Test
    fun `LlmGate blank model`() {
        assertGateIssue(issues(pipeline(start(), llm(model = ""))), "llm1", "endpointConfig.model")
    }

    @Test
    fun `LlmGate missing credentialId and apiKeyVar`() {
        assertGateIssue(issues(pipeline(start(), llm(credentialId = ""))), "llm1", "endpointConfig.credentialId")
        assertGateIssue(
            issues(pipeline(start(), llm(credentialId = "", apiKeyVar = " "))),
            "llm1",
            "endpointConfig.credentialId",
        )
    }

    @Test
    fun `LlmGate apiKeyVar alone satisfies credential rule`() {
        val result = issues(pipeline(start(), llm(credentialId = "", apiKeyVar = "OPENAI_KEY")))
        assertFalse(result.any { it.field == "endpointConfig.credentialId" })
    }

    @Test
    fun `LlmGate non-positive maxToolRounds`() {
        assertGateIssue(issues(pipeline(start(), llm(maxToolRounds = 0))), "llm1", "maxToolRounds")
    }

    @Test
    fun `LlmGate blank tool name`() {
        val result = issues(pipeline(start(), llm(tools = listOf("a", " "))))
        assertGateIssue(result, "llm1", "tools")
        assertMessageMentions(result, "tools", "blank")
    }

    @Test
    fun `LlmGate duplicate tool names`() {
        val result = issues(pipeline(start(), llm(tools = listOf("a", "a"))))
        assertGateIssue(result, "llm1", "tools")
        assertMessageMentions(result, "tools", "duplicated")
    }

    @Test
    fun `LogicGate null defaultPort`() {
        assertGateIssue(issues(pipeline(start(), logic(defaultPort = null))), "logic1", "defaultPort")
    }

    @Test
    fun `LogicGate blank defaultPort`() {
        assertGateIssue(issues(pipeline(start(), logic(defaultPort = ""))), "logic1", "defaultPort")
    }

    @Test
    fun `LogicGate blank branch port`() {
        val gate = logic(branches = listOf(Branch("", BranchCondition.Always)))
        assertGateIssue(issues(pipeline(start(), gate)), "logic1", "branch.port")
    }

    @Test
    fun `ToolGate blank toolName`() {
        val gate = ToolGate(id = GateId("tool1"), label = "Tool", toolName = "")
        assertGateIssue(issues(pipeline(start(), gate)), "tool1", "toolName")
    }

    @Test
    fun `BashGate blank command`() {
        val gate = BashGate(id = GateId("bash1"), label = "Bash", command = "")
        assertGateIssue(issues(pipeline(start(), gate)), "bash1", "command")
    }

    @Test
    fun `BashGate blank workingDirectory`() {
        val gate = BashGate(id = GateId("bash1"), label = "Bash", command = "ls", workingDirectory = " ")
        assertGateIssue(issues(pipeline(start(), gate)), "bash1", "workingDirectory")
    }

    @Test
    fun `BashGate non-positive timeout`() {
        val gate = BashGate(id = GateId("bash1"), label = "Bash", command = "ls", timeoutSeconds = 0)
        assertGateIssue(issues(pipeline(start(), gate)), "bash1", "timeoutSeconds")
    }

    @Test
    fun `BashGate blank environment key`() {
        val gate = BashGate(id = GateId("bash1"), label = "Bash", command = "ls", environment = mapOf("" to "x"))
        assertGateIssue(issues(pipeline(start(), gate)), "bash1", "environment")
    }

    @Test
    fun `BashGate blank outputMapping key or value`() {
        val blankKey = BashGate(id = GateId("bash1"), label = "Bash", command = "ls", outputMapping = mapOf("" to "x"))
        assertGateIssue(issues(pipeline(start(), blankKey)), "bash1", "outputMapping")
        val blankValue = BashGate(id = GateId("bash1"), label = "Bash", command = "ls", outputMapping = mapOf("x" to ""))
        assertGateIssue(issues(pipeline(start(), blankValue)), "bash1", "outputMapping")
    }

    @Test
    fun `ReadFileGate blank path and outputKey`() {
        val gate = ReadFileGate(id = GateId("read1"), label = "Read", path = "", outputKey = "")
        val result = issues(pipeline(start(), gate))
        assertGateIssue(result, "read1", "path")
        assertGateIssue(result, "read1", "outputKey")
    }

    @Test
    fun `WriteFileGate blank path and contentKey`() {
        val gate = WriteFileGate(id = GateId("write1"), label = "Write", path = "", contentKey = "")
        val result = issues(pipeline(start(), gate))
        assertGateIssue(result, "write1", "path")
        assertGateIssue(result, "write1", "contentKey")
    }

    @Test
    fun `edge to unknown gate produces EDGE issue carrying the missing id`() {
        val p = pipeline(start(), edges = listOf(PipelineEdge(from = GateId("start"), to = GateId("ghost"))))
        val result = issues(p)
        assertIssue(result, IssueScope.EDGE, "ghost", "to")
        assertFalse(result.any { it.field == "edges" })
    }

    @Test
    fun `edge from unknown gate produces EDGE issue carrying the missing id`() {
        val p = pipeline(start(), edges = listOf(PipelineEdge(from = GateId("ghost"), to = GateId("start"))))
        assertIssue(issues(p), IssueScope.EDGE, "ghost", "from")
    }

    @Test
    fun `fromPort not on source gate produces EDGE issue`() {
        val p = pipeline(
            start(),
            end(),
            edges = listOf(PipelineEdge(from = GateId("start"), fromPort = "nonexistent", to = GateId("end"))),
        )
        assertIssue(issues(p), IssueScope.EDGE, "start", "fromPort")
    }

    @Test
    fun `LogicGate accepts branch ports and defaultPort as fromPort`() {
        val p = pipeline(
            start(),
            logic(),
            end(),
            edges = listOf(
                PipelineEdge(from = GateId("start"), to = GateId("logic1")),
                PipelineEdge(from = GateId("logic1"), fromPort = "yes", to = GateId("end")),
                PipelineEdge(from = GateId("logic1"), fromPort = "default", to = GateId("end")),
            ),
        )
        assertTrue(issues(p).isEmpty())
        val bad = pipeline(
            start(),
            logic(),
            end(),
            edges = listOf(PipelineEdge(from = GateId("logic1"), fromPort = "out", to = GateId("end"))),
        )
        assertIssue(issues(bad), IssueScope.EDGE, "logic1", "fromPort")
    }

    @Test
    fun `toPort other than in produces EDGE issue`() {
        val p = pipeline(
            start(),
            end(),
            edges = listOf(PipelineEdge(from = GateId("start"), to = GateId("end"), toPort = "input")),
        )
        assertIssue(issues(p), IssueScope.EDGE, "end", "toPort")
    }

    @Test
    fun `edge into an InputGate produces toPort issue`() {
        val p = pipeline(
            start(),
            end(),
            edges = listOf(PipelineEdge(from = GateId("end"), to = GateId("start"))),
        )
        assertIssue(issues(p), IssueScope.EDGE, "start", "toPort")
    }

    @Test
    fun `duplicate outgoing edge on same port produces EDGE issue`() {
        val other = OutputGate(id = GateId("other"), label = "Other")
        val p = pipeline(
            start(),
            end(),
            other,
            edges = listOf(
                PipelineEdge(from = GateId("start"), to = GateId("end")),
                PipelineEdge(from = GateId("start"), to = GateId("other")),
            ),
        )
        val result = issues(p)
        assertIssue(result, IssueScope.EDGE, "start", "fromPort")
        assertEquals(1, result.size)
        assertEquals("Gate 'start' already has an edge from port 'out'", result.first().message)
    }

    @Test
    fun `cycle produces PIPELINE issue`() {
        val a = llm(id = "a")
        val b = llm(id = "b")
        val p = pipeline(
            start(),
            a,
            b,
            edges = listOf(
                PipelineEdge(from = GateId("start"), to = GateId("a")),
                PipelineEdge(from = GateId("a"), to = GateId("b")),
                PipelineEdge(from = GateId("b"), to = GateId("a")),
            ),
        )
        val result = issues(p)
        assertIssue(result, IssueScope.PIPELINE, null, "edges")
        assertEquals("Pipeline contains a cycle — DAG required", result.first { it.field == "edges" }.message)
    }

    @Test
    fun `validate throws with every issue listed`() {
        val p = pipeline(
            start(),
            llm(promptTemplate = "", model = ""),
            edges = listOf(PipelineEdge(from = GateId("start"), to = GateId("ghost"))),
            id = "",
        )
        try {
            validator.validate(p)
            fail("expected PipelineValidationException")
        } catch (e: PipelineValidationException) {
            val fields = e.issues.map { it.field }
            assertEquals(listOf("id", "promptTemplate", "endpointConfig.model", "to"), fields)
            val message = e.message ?: ""
            assertTrue(message.contains("Pipeline id is required"))
            assertTrue(message.contains("ghost"))
        }
    }
}
