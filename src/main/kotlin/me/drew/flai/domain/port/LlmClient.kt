package me.drew.flai.domain.port

import me.drew.flai.domain.model.LlmEndpointConfig

interface LlmClient {
    suspend fun complete(config: LlmEndpointConfig, prompt: String, apiKey: String? = null): String
}
