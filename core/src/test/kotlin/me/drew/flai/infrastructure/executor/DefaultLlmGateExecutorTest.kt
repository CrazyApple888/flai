package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.port.LlmClient
import me.drew.flai.domain.port.LlmCompletion
import me.drew.flai.domain.port.LlmConversation
import me.drew.flai.domain.port.LlmToolCall
import me.drew.flai.domain.port.TemplateRenderer
import me.drew.flai.domain.port.Tool
import me.drew.flai.domain.port.ToolInputSchema
import me.drew.flai.domain.port.ToolSchemaProperty
import me.drew.flai.domain.port.ToolSchemaType
import me.drew.flai.domain.port.ToolResult
import me.drew.flai.infrastructure.tool.DefaultToolRegistry
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
            override suspend fun complete(config: me.drew.flai.domain.model.LlmEndpointConfig, conversation: LlmConversation, tools: List<me.drew.flai.domain.port.LlmToolDefinition>, apiKey: String?): LlmCompletion {
                capturedPrompt = conversation.messages.first().content
                return LlmCompletion(response)
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
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), fakeSkillLoader())
        val context = ExecutionContext(mapOf("name" to "World"))

        val result = executor.execute(gate(promptTemplate = "Hello {{name}}"), context)

        assertTrue(result is GateResult.Success)
        assertEquals("Hello World", getPrompt())
    }

    @Test
    fun `one skill - merged prompt is skillBody plus rendered template`() = runBlocking {
        val skillBodies = mapOf("skill1.md" to "You are an expert.")
        val (client, getPrompt) = capturingLlmClient()
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), fakeSkillLoader(skillBodies))
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
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), fakeSkillLoader(skillBodies))
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
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), fakeSkillLoader(skillBodies))
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
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), fakeSkillLoader(skillBodies))
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
            override suspend fun complete(config: me.drew.flai.domain.model.LlmEndpointConfig, conversation: LlmConversation, tools: List<me.drew.flai.domain.port.LlmToolDefinition>, apiKey: String?): LlmCompletion {
                llmCalled = true
                return LlmCompletion("response")
            }
        }
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), failingSkillLoader(error))
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
        val executor = DefaultLlmGateExecutor(client, DefaultToolRegistry(), fakeRenderer(), fakeSkillLoader(skillBodies))
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

    @Test
    fun `allowlisted tool calls execute sequentially and are returned to the next completion`() = runBlocking {
        val registry = DefaultToolRegistry()
        var receivedInput: Map<String, Any?>? = null
        registry.register(object : Tool {
            override val name = "test.echo"
            override val description = "Echo input"
            override val inputSchema = ToolInputSchema(
                mapOf("value" to ToolSchemaProperty(ToolSchemaType.STRING)),
                listOf("value"),
            )

            override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): ToolResult {
                receivedInput = inputs
                return ToolResult(mapOf("echo" to inputs["value"]))
            }
        })
        val conversations = mutableListOf<LlmConversation>()
        val client = object : LlmClient {
            override suspend fun complete(
                config: LlmEndpointConfig,
                conversation: LlmConversation,
                tools: List<me.drew.flai.domain.port.LlmToolDefinition>,
                apiKey: String?,
            ): LlmCompletion {
                conversations += conversation
                return if (conversations.size == 1) {
                    LlmCompletion(toolCalls = listOf(LlmToolCall("call-1", tools.single().name, "{\"value\":\"hello\"}")))
                } else {
                    LlmCompletion("finished")
                }
            }
        }
        val reports = mutableListOf<String>()
        val executor = DefaultLlmGateExecutor(client, registry, fakeRenderer(), fakeSkillLoader())
        val result = executor.execute(gate().copy(tools = listOf("test.echo")), ExecutionContext()) { report ->
            reports += "${report.toolName}:${report.round}:${report.succeeded}"
        }

        assertEquals(mapOf("value" to "hello"), receivedInput)
        assertEquals(listOf("test.echo:1:true"), reports)
        assertEquals(3, conversations.last().messages.size)
        assertEquals("finished", (result as GateResult.Success).outputs["response"])
    }

    @Test
    fun `reported tool errors are surfaced as error results and failed reports`() = runBlocking {
        val registry = DefaultToolRegistry()
        registry.register(object : Tool {
            override val name = "test.failure"
            override val description = "Fails"
            override val inputSchema = ToolInputSchema(emptyMap())

            override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): ToolResult {
                return ToolResult(mapOf("error" to "expected failure"), isError = true)
            }
        })
        var toolResultIsError: Boolean? = null
        val client = object : LlmClient {
            private var callCount = 0

            override suspend fun complete(
                config: LlmEndpointConfig,
                conversation: LlmConversation,
                tools: List<me.drew.flai.domain.port.LlmToolDefinition>,
                apiKey: String?,
            ): LlmCompletion {
                callCount += 1
                if (callCount == 2) {
                    toolResultIsError = conversation.messages.last().toolResults.single().isError
                    return LlmCompletion("done")
                }
                return LlmCompletion(toolCalls = listOf(LlmToolCall("call-1", tools.single().name, "{}")))
            }
        }
        val reports = mutableListOf<Boolean>()
        val executor = DefaultLlmGateExecutor(client, registry, fakeRenderer(), fakeSkillLoader())

        executor.execute(gate().copy(tools = listOf("test.failure")), ExecutionContext()) { report ->
            reports += report.succeeded
        }

        assertEquals(true, toolResultIsError)
        assertEquals(listOf(false), reports)
    }
}
