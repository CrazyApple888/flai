package me.drew.flai.infrastructure.executor

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import me.drew.flai.domain.executor.GateExecutionReport
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.port.LlmClient
import me.drew.flai.domain.port.LlmConversation
import me.drew.flai.domain.port.LlmMessage
import me.drew.flai.domain.port.LlmMessageRole
import me.drew.flai.domain.port.LlmToolDefinition
import me.drew.flai.domain.port.LlmToolResult
import me.drew.flai.domain.port.Tool
import me.drew.flai.domain.port.ToolRegistry
import kotlin.time.TimeSource

class LlmToolCallLoop(
    private val llmClient: LlmClient,
    private val toolRegistry: ToolRegistry,
    private val gson: Gson,
) {
    suspend fun run(
        gate: LlmGate,
        prompt: String,
        context: ExecutionContext,
        report: suspend (GateExecutionReport) -> Unit,
    ): Map<String, Any?> {
        val tools = gate.tools.mapNotNull(toolRegistry::get)
        val aliases = ToolAliasMapper(tools)
        val definitions = tools.map { tool ->
            LlmToolDefinition(aliases.aliasFor(tool.name), tool.description, tool.inputSchema)
        }
        val apiKey = gate.endpointConfig.apiKeyVar?.let { context.get(it)?.toString() }
        val conversation = mutableListOf(LlmMessage(LlmMessageRole.USER, prompt))
        var round = 0
        while (true) {
            val completion = llmClient.complete(gate.endpointConfig, LlmConversation(conversation.toList()), definitions, apiKey)
            if (completion.toolCalls.isEmpty()) {
                return mapOf("response" to (completion.content ?: ""))
            }
            if (round >= gate.maxToolRounds) {
                throw IllegalStateException("LLM gate '${gate.id.value}' exceeded maxToolRounds (${gate.maxToolRounds})")
            }
            round += 1
            conversation += LlmMessage(LlmMessageRole.ASSISTANT, completion.content, completion.toolCalls)
            conversation += LlmMessage(
                LlmMessageRole.TOOL,
                toolResults = completion.toolCalls.map { call ->
                    val tool = aliases.toolFor(call.name)
                    if (tool == null) {
                        LlmToolResult(call.id, "Unknown or unavailable tool '${call.name}'", true)
                    } else {
                        invokeTool(tool, call.argumentsJson, context, round, report, call.id)
                    }
                },
            )
        }
    }

    private suspend fun invokeTool(
        tool: Tool,
        argumentsJson: String,
        context: ExecutionContext,
        round: Int,
        report: suspend (GateExecutionReport) -> Unit,
        callId: String,
    ): LlmToolResult {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val result = tool.invoke(parseInputs(argumentsJson), context)
            report(GateExecutionReport(tool.name, round, mark.elapsedNow().inWholeMilliseconds, !result.isError))
            LlmToolResult(callId, gson.toJson(result.outputs), result.isError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report(GateExecutionReport(tool.name, round, mark.elapsedNow().inWholeMilliseconds, false))
            LlmToolResult(callId, e.message ?: e::class.simpleName ?: "Tool invocation failed", true)
        }
    }

    private fun parseInputs(argumentsJson: String): Map<String, Any?> {
        val element = JsonParser.parseString(argumentsJson)
        if (!element.isJsonObject) {
            throw IllegalArgumentException("Tool arguments must be a JSON object")
        }
        @Suppress("UNCHECKED_CAST")
        return gson.fromJson(element as JsonObject, Map::class.java) as Map<String, Any?>
    }
}

private class ToolAliasMapper(tools: List<Tool>) {
    private val aliases = tools.associateBy { providerAlias(it.name) }

    init {
        if (aliases.size != tools.size) {
            throw IllegalArgumentException("Tool names produce colliding provider aliases")
        }
    }

    fun aliasFor(name: String): String = providerAlias(name)

    fun toolFor(alias: String): Tool? = aliases[alias]

    private fun providerAlias(name: String): String = buildString {
        append("flai_")
        name.forEach { character ->
            when {
                character.isLetterOrDigit() -> append(character)
                character == '_' -> append("__")
                else -> append('_').append(character.code.toString(16)).append('_')
            }
        }
    }
}
