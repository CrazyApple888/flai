package me.drew.flai.infrastructure.llm

import com.google.gson.Gson
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.port.LlmClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class HttpLlmClient : LlmClient {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    private val gson = Gson()

    override suspend fun complete(config: LlmEndpointConfig, prompt: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = resolveCredential(config.credentialId)
                ?: throw IllegalStateException("No credential found for id '${config.credentialId}'")

            val body = buildRequestBody(config, prompt)
            val json = gson.toJson(body)

            val request = HttpRequest.newBuilder()
                .uri(URI.create(config.url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(120))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("LLM API error ${response.statusCode()}: ${response.body()}")
            }

            extractContent(response.body())
        }

    private fun resolveCredential(credentialId: String): String? {
        val attrs = CredentialAttributes(generateServiceName("flai", credentialId))
        return PasswordSafe.instance.getPassword(attrs)
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildRequestBody(config: LlmEndpointConfig, prompt: String): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "model" to config.model,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 4096,
        )
        config.params.forEach { (k, v) -> base[k] = v }
        return base
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractContent(responseBody: String): String {
        return try {
            val map = gson.fromJson(responseBody, Map::class.java) as Map<String, Any?>
            // Anthropic format: content[0].text
            val content = map["content"] as? List<*>
            if (content != null) {
                val first = content.firstOrNull() as? Map<*, *>
                return first?.get("text")?.toString() ?: ""
            }
            // OpenAI format: choices[0].message.content
            val choices = map["choices"] as? List<*>
            val first = choices?.firstOrNull() as? Map<*, *>
            val message = first?.get("message") as? Map<*, *>
            message?.get("content")?.toString() ?: responseBody
        } catch (e: Exception) {
            responseBody
        }
    }
}
