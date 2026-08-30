package me.drew.flai.infrastructure.llm

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.model.LlmProvider
import me.drew.flai.domain.port.CredentialResolver
import me.drew.flai.domain.port.LlmClient
import me.drew.flai.domain.port.LlmCompletion
import me.drew.flai.domain.port.LlmConversation
import me.drew.flai.domain.port.LlmToolDefinition
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpLlmClient(
    private val credentialResolver: CredentialResolver,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
    private val gson: Gson = Gson(),
    private val codecs: Map<LlmProvider, LlmProtocolCodec> = mapOf(
        LlmProvider.OPENAI to OpenAiChatCompletionsCodec(gson),
        LlmProvider.ANTHROPIC to AnthropicMessagesCodec(gson),
    ),
) : LlmClient {
    override suspend fun complete(
        config: LlmEndpointConfig,
        conversation: LlmConversation,
        tools: List<LlmToolDefinition>,
        apiKey: String?,
    ): LlmCompletion = withContext(Dispatchers.IO) {
        val resolvedApiKey = apiKey
            ?: credentialResolver.resolve(config.credentialId).takeIf { !it.isNullOrBlank() }
            ?: throw IllegalStateException("No API key: set apiKeyVar in pipeline or provide credential '${config.credentialId}'")
        val codec = codecs[config.provider]
            ?: throw IllegalStateException("No LLM protocol codec for ${config.provider}")
        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .apply { codec.applyHeaders(this, resolvedApiKey) }
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(codec.buildRequest(config, conversation, tools))))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("LLM API error ${response.statusCode()}: ${response.body()}")
        }
        codec.parseResponse(response.body())
    }
}
