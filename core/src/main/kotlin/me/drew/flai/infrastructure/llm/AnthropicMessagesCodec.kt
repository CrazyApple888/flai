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

internal class AnthropicMessagesCodec(private val gson: Gson) : LlmProtocolCodec {
    override fun applyHeaders(builder: HttpRequest.Builder, apiKey: String) {
        builder.header("x-api-key", apiKey)
        builder.header("anthropic-version", "2023-06-01")
    }

    override fun buildRequest(
        config: LlmEndpointConfig,
        conversation: LlmConversation,
        tools: List<LlmToolDefinition>,
    ): JsonObject = JsonObject().apply {
        addProperty("model", config.model)
        add("messages", JsonArray().also { messages ->
            conversation.messages.forEach { messages.add(anthropicMessage(it)) }
        })
        if (tools.isNotEmpty()) {
            add("tools", JsonArray().also { definitions ->
                tools.forEach { tool ->
                    definitions.add(JsonObject().apply {
                        addProperty("name", tool.name)
                        addProperty("description", tool.description)
                        add("input_schema", toolSchemaJson(tool.inputSchema))
                    })
                }
            })
        }
        if (!config.params.containsKey("max_tokens")) {
            addProperty("max_tokens", 4096)
        }
        config.params.forEach { (key, value) -> add(key, gson.toJsonTree(value)) }
    }

    override fun parseResponse(body: String): LlmCompletion {
        val content = JsonParser.parseString(body).asJsonObject.getAsJsonArray("content") ?: JsonArray()
        val text = content.filter { it.asJsonObject.get("type")?.asString == "text" }
            .joinToString("") { it.asJsonObject.get("text").asString }
        val calls = content.filter { it.asJsonObject.get("type")?.asString == "tool_use" }.map { element ->
            val call = element.asJsonObject
            LlmToolCall(call.get("id").asString, call.get("name").asString, gson.toJson(call.get("input") ?: JsonObject()))
        }
        return LlmCompletion(text.takeIf { it.isNotEmpty() }, calls)
    }

    private fun anthropicMessage(message: LlmMessage): JsonObject = JsonObject().apply {
        when (message.role) {
            LlmMessageRole.USER -> {
                addProperty("role", "user")
                addProperty("content", message.content ?: "")
            }
            LlmMessageRole.ASSISTANT -> {
                addProperty("role", "assistant")
                add("content", JsonArray().also { blocks ->
                    message.content?.let { text ->
                        blocks.add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", text)
                        })
                    }
                    message.toolCalls.forEach { call ->
                        blocks.add(JsonObject().apply {
                            addProperty("type", "tool_use")
                            addProperty("id", call.id)
                            addProperty("name", call.name)
                            add("input", JsonParser.parseString(call.argumentsJson))
                        })
                    }
                })
            }
            LlmMessageRole.TOOL -> {
                addProperty("role", "user")
                add("content", JsonArray().also { blocks ->
                    message.toolResults.forEach { result ->
                        blocks.add(JsonObject().apply {
                            addProperty("type", "tool_result")
                            addProperty("tool_use_id", result.callId)
                            addProperty("content", result.content)
                            if (result.isError) {
                                addProperty("is_error", true)
                            }
                        })
                    }
                })
            }
        }
    }
}
