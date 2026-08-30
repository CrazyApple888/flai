package me.drew.flai.infrastructure.llm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.port.LlmCompletion
import me.drew.flai.domain.port.LlmConversation
import me.drew.flai.domain.port.LlmMessage
import me.drew.flai.domain.port.LlmMessageRole
import me.drew.flai.domain.port.LlmToolCall
import me.drew.flai.domain.port.LlmToolDefinition
import java.net.http.HttpRequest

internal class OpenAiChatCompletionsCodec(private val gson: Gson) : LlmProtocolCodec {
    override fun applyHeaders(builder: HttpRequest.Builder, apiKey: String) {
        builder.header("Authorization", "Bearer $apiKey")
    }

    override fun buildRequest(
        config: LlmEndpointConfig,
        conversation: LlmConversation,
        tools: List<LlmToolDefinition>,
    ): JsonObject = JsonObject().apply {
        addProperty("model", config.model)
        add("messages", openAiMessages(conversation))
        if (tools.isNotEmpty()) {
            add("tools", openAiTools(tools))
        }
        if (!config.params.containsKey("max_tokens")) {
            addProperty("max_tokens", 4096)
        }
        config.params.forEach { (key, value) -> add(key, gson.toJsonTree(value)) }
    }

    override fun parseResponse(body: String): LlmCompletion {
        val message = JsonParser.parseString(body).asJsonObject
            .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject?.getAsJsonObject("message")
            ?: throw IllegalStateException("OpenAI response has no choices.message")
        val content = message.get("content")?.takeUnless { it.isJsonNull }?.asString
        val calls = message.getAsJsonArray("tool_calls")?.map { element ->
            val call = element.asJsonObject
            val function = call.getAsJsonObject("function")
            LlmToolCall(call.get("id").asString, function.get("name").asString, function.get("arguments").asString)
        } ?: emptyList()
        return LlmCompletion(content, calls)
    }

    private fun openAiMessages(conversation: LlmConversation): JsonArray = JsonArray().also { messages ->
        conversation.messages.forEach { message ->
            if (message.role == LlmMessageRole.TOOL) {
                message.toolResults.forEach { result ->
                    messages.add(JsonObject().apply {
                        addProperty("role", "tool")
                        addProperty("tool_call_id", result.callId)
                        addProperty("content", result.content)
                    })
                }
            } else {
                messages.add(openAiMessage(message))
            }
        }
    }

    private fun openAiTools(tools: List<LlmToolDefinition>): JsonArray = JsonArray().also { definitions ->
        tools.forEach { tool ->
            definitions.add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", tool.name)
                    addProperty("description", tool.description)
                    add("parameters", toolSchemaJson(tool.inputSchema))
                })
            })
        }
    }

    private fun openAiMessage(message: LlmMessage): JsonObject = JsonObject().apply {
        when (message.role) {
            LlmMessageRole.USER -> {
                addProperty("role", "user")
                addProperty("content", message.content ?: "")
            }
            LlmMessageRole.ASSISTANT -> {
                addProperty("role", "assistant")
                message.content?.let { addProperty("content", it) }
                if (message.toolCalls.isNotEmpty()) {
                    add("tool_calls", JsonArray().also { calls ->
                        message.toolCalls.forEach { call ->
                            calls.add(JsonObject().apply {
                                addProperty("id", call.id)
                                addProperty("type", "function")
                                add("function", JsonObject().apply {
                                    addProperty("name", call.name)
                                    addProperty("arguments", call.argumentsJson)
                                })
                            })
                        }
                    })
                }
            }
            LlmMessageRole.TOOL -> Unit
        }
    }
}
