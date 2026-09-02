package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.service.PipelineValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser accepts the YAML the visual editor writes for gates the user has not configured yet;
 * [PipelineValidator] is what reports the missing values before a run.
 */
class YamlPipelineParserUnconfiguredGateTest {

    private val parser = YamlPipelineParser()
    private val validator = PipelineValidator()

    @Test
    fun `llm endpoint without credentialId and apiKeyVar parses`() {
        val gate = parser.parse(llmYaml).gates[GateId("ask")] as LlmGate
        assertEquals("", gate.endpointConfig.credentialId)
        assertNull(gate.endpointConfig.apiKeyVar)
    }

    @Test
    fun `validator still reports an llm endpoint without credentialId and apiKeyVar`() {
        assertGateIssue(parser.parse(llmYaml), "ask", "endpointConfig.credentialId")
    }

    @Test
    fun `bash gate with blank command parses to an empty command`() {
        val gate = parser.parse(bashYaml("command: ''")).gates[GateId("run")] as BashGate
        assertEquals("", gate.command)
        assertEquals(".", gate.workingDirectory)
    }

    @Test
    fun `validator still reports a bash gate with a blank command`() {
        assertGateIssue(parser.parse(bashYaml("command: ''")), "run", "command")
    }

    @Test
    fun `bash gate with blank workingDirectory parses to a blank workingDirectory`() {
        val gate = parser.parse(bashYaml("command: printf hello\nworkingDirectory: ''"))
            .gates[GateId("run")] as BashGate
        assertEquals("printf hello", gate.command)
        assertEquals("", gate.workingDirectory)
    }

    @Test
    fun `validator still reports a bash gate with a blank workingDirectory`() {
        assertGateIssue(
            parser.parse(bashYaml("command: printf hello\nworkingDirectory: ''")),
            "run",
            "workingDirectory",
        )
    }

    private fun assertGateIssue(pipeline: Pipeline, gateId: String, field: String) {
        val issues = validator.collectIssues(pipeline)
        assertTrue(
            "Expected an issue on '$gateId' / '$field', got $issues",
            issues.any { it.gateId == gateId && it.field == field },
        )
    }

    private val llmYaml = """
        id: p
        name: P
        entry: ask
        gates:
          ask:
            type: llm
            label: Ask
            promptTemplate: Hello
            endpoint:
              url: https://api.openai.com/v1/chat/completions
              model: gpt-4o
        edges: []
    """.trimIndent()

    private fun bashYaml(body: String): String {
        return buildString {
            appendLine("id: p")
            appendLine("name: P")
            appendLine("entry: run")
            appendLine("gates:")
            appendLine("  run:")
            appendLine("    type: bash")
            appendLine(body.lines().joinToString("\n") { "    $it" })
            appendLine("edges: []")
        }
    }
}
