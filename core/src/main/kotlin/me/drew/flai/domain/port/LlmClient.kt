package me.drew.flai.domain.port

import me.drew.flai.domain.model.LlmEndpointConfig

interface LlmClient {
    suspend fun complete(
        config: LlmEndpointConfig,
        conversation: LlmConversation,
        tools: List<LlmToolDefinition> = emptyList(),
        apiKey: String? = null,
    ): LlmCompletion
}

data class LlmConversation(val messages: List<LlmMessage>)

data class LlmMessage(
    val role: LlmMessageRole,
    val content: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolResults: List<LlmToolResult> = emptyList(),
)

enum class LlmMessageRole { USER, ASSISTANT, TOOL }

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: ToolInputSchema,
)

data class LlmToolCall(val id: String, val name: String, val argumentsJson: String)

data class LlmToolResult(val callId: String, val content: String, val isError: Boolean = false)

data class LlmCompletion(val content: String? = null, val toolCalls: List<LlmToolCall> = emptyList())
