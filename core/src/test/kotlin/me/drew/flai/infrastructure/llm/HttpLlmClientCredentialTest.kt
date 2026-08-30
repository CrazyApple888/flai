package me.drew.flai.infrastructure.llm

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.LlmEndpointConfig
import me.drew.flai.domain.port.LlmConversation
import me.drew.flai.domain.port.LlmMessage
import me.drew.flai.domain.port.LlmMessageRole
import me.drew.flai.domain.port.CredentialResolver
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HttpLlmClientCredentialTest {

    private val config = LlmEndpointConfig(
        url = "http://localhost:9",
        credentialId = "my-cred",
        model = "test-model",
    )

    @Test
    fun `fails with clear message when resolver returns null`() = runBlocking {
        val client = HttpLlmClient(CredentialResolver { null })
        try {
            client.complete(config, LlmConversation(listOf(LlmMessage(LlmMessageRole.USER, "prompt"))))
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("my-cred"))
        }
    }

    @Test
    fun `fails when resolver returns blank`() = runBlocking {
        val client = HttpLlmClient(CredentialResolver { "" })
        try {
            client.complete(config, LlmConversation(listOf(LlmMessage(LlmMessageRole.USER, "prompt"))))
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("my-cred"))
        }
    }
}
