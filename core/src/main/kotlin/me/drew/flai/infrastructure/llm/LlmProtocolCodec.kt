package me.drew.flai.infrastructure.llm

import com.google.gson.JsonObject
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.port.LlmCompletion
import me.drew.flai.domain.port.LlmConversation
import me.drew.flai.domain.port.LlmToolDefinition
import java.net.http.HttpRequest

interface LlmProtocolCodec {
    fun applyHeaders(builder: HttpRequest.Builder, apiKey: String)
    fun buildRequest(config: LlmEndpointConfig, conversation: LlmConversation, tools: List<LlmToolDefinition>): JsonObject
    fun parseResponse(body: String): LlmCompletion
}
