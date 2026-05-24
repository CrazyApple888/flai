package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.port.LlmClient
import me.drew.flai.domain.port.TemplateRenderer
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLlmGateExecutorTest {

    private val endpointConfig = LlmEndpointConfig(
        url = "https://api.anthropic.com/v1/messages",
        credentialId = "test-key",
        model = "claude-sonnet-4-6",
    )

    private fun gate(
        promptTemplate: String = "Hello world",
        skills: List<String> = emptyList(),
    ) = LlmGate(
        id = GateId("test-gate"),
        label = "Test Gate",
        promptTemplate = promptTemplate,
        skills = skills,
        endpointConfig = endpointConfig,
    )

    private fun fakeRenderer(): TemplateRenderer = SimpleTemplateRenderer()

    private fun capturingLlmClient(response: String = "LLM response"): Pair<LlmClient, () -> String?> {
        var capturedPrompt: String? = null
        val client = object : LlmClient {
            override suspend fun complete(config: me.drew.flai.domain.model.LlmEndpointConfig, prompt: String, apiKey: String?): String {
                capturedPrompt = prompt
                return response
            }
        }
        return client to { capturedPrompt }
    }

    private fun fakeSkillLoader(bodies: Map<String, String> = emptyMap()): SkillLoader {
        return object : SkillLoader("") {
            override suspend fun load(skillPaths: List<String>): List<String> {
                return skillPaths.map { path ->
                    bodies[path] ?: throw SkillLoadException("Skill file not found: $path")
                }
            }
        }
    }

    private fun failingSkillLoader(error: SkillLoadException): SkillLoader {
        return object : SkillLoader("") {
            override suspend fun load(skillPaths: List<String>): List<String> {
                throw error
            }
        }
    }

    @Test
    fun `no skills - prompt equals rendered template (regression)`() = runBlocking {
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), fakeSkillLoader())
        val context = ExecutionContext(mapOf("name" to "World"))

        val result = executor.execute(gate(promptTemplate = "Hello {{name}}"), context)

        assertTrue(result is GateResult.Success)
        assertEquals("Hello World", getPrompt())
    }

    @Test
    fun `one skill - merged prompt is skillBody plus rendered template`() = runBlocking {
        val skillBodies = mapOf("skill1.md" to "You are an expert.")
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), fakeSkillLoader(skillBodies))
        val context = ExecutionContext()

        val result = executor.execute(gate(promptTemplate = "Review this code.", skills = listOf("skill1.md")), context)

        assertTrue(result is GateResult.Success)
        assertEquals("You are an expert.\n\nReview this code.", getPrompt())
    }

    @Test
    fun `two skills - merged prompt is skill1 plus skill2 plus rendered template`() = runBlocking {
        val skillBodies = mapOf(
            "skill1.md" to "Persona instructions.",
            "skill2.md" to "Output format instructions.",
        )
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), fakeSkillLoader(skillBodies))
        val context = ExecutionContext()

        val result = executor.execute(
            gate(promptTemplate = "Do the task.", skills = listOf("skill1.md", "skill2.md")),
            context,
        )

        assertTrue(result is GateResult.Success)
        assertEquals("Persona instructions.\n\nOutput format instructions.\n\nDo the task.", getPrompt())
    }

    @Test
    fun `skills present with empty promptTemplate - merged prompt is skill content only`() = runBlocking {
        val skillBodies = mapOf(
            "skill1.md" to "Skill A.",
            "skill2.md" to "Skill B.",
        )
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), fakeSkillLoader(skillBodies))
        val context = ExecutionContext()

        val result = executor.execute(
            gate(promptTemplate = "", skills = listOf("skill1.md", "skill2.md")),
            context,
        )

        assertTrue(result is GateResult.Success)
        assertEquals("Skill A.\n\nSkill B.", getPrompt())
    }

    @Test
    fun `empty skill body in the middle - exact merged string with doubled blank line`() = runBlocking {
        val skillBodies = mapOf(
            "a.md" to "A",
            "b.md" to "",
            "c.md" to "B",
        )
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), fakeSkillLoader(skillBodies))
        val context = ExecutionContext()

        val result = executor.execute(
            gate(promptTemplate = "T", skills = listOf("a.md", "b.md", "c.md")),
            context,
        )

        assertTrue(result is GateResult.Success)
        assertEquals("A\n\n\n\nB\n\nT", getPrompt())
    }

    @Test
    fun `SkillLoadException from loader returns Failure with retryable false and LLM is not called`() = runBlocking {
        val error = SkillLoadException("Skill file not found: /missing/skill.md")
        var llmCalled = false
        val client = object : LlmClient {
            override suspend fun complete(config: me.drew.flai.domain.model.LlmEndpointConfig, prompt: String, apiKey: String?): String {
                llmCalled = true
                return "response"
            }
        }
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), failingSkillLoader(error))
        val context = ExecutionContext()

        val result = executor.execute(gate(skills = listOf("missing.md")), context)

        assertTrue(result is GateResult.Failure)
        assertFalse("LLM must not be called when skills fail to load", llmCalled)
        assertFalse("retryable must be false for skill load errors", (result as GateResult.Failure).retryable)
    }

    @Test
    fun `template substitution applied to promptTemplate but not to skill bodies`() = runBlocking {
        val skillBodies = mapOf("skill.md" to "Use {{var}} literally.")
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, fakeRenderer(), fakeSkillLoader(skillBodies))
        val context = ExecutionContext(mapOf("var" to "SUBSTITUTED"))

        val result = executor.execute(
            gate(promptTemplate = "Template with {{var}}.", skills = listOf("skill.md")),
            context,
        )

        assertTrue(result is GateResult.Success)
        val prompt = getPrompt()!!
        assertTrue(
            "Skill body should contain literal {{var}}",
            prompt.contains("Use {{var}} literally.")
        )
        assertTrue(
            "PromptTemplate should have {{var}} substituted",
            prompt.contains("Template with SUBSTITUTED.")
        )
    }
}
