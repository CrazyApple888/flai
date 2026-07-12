package me.drew.flai.cli.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvCredentialResolverTest {

    @Test
    fun `resolves env var with mangled id`() {
        val resolver = EnvCredentialResolver { name ->
            if (name == "FLAI_CREDENTIAL_MY_OPENAI_KEY") {
                "sk-123"
            } else {
                null
            }
        }
        assertEquals("sk-123", resolver.resolve("my-openai.key"))
    }

    @Test
    fun `missing env var returns null`() {
        val resolver = EnvCredentialResolver { null }
        assertNull(resolver.resolve("anything"))
    }

    @Test
    fun `blank credential id returns null`() {
        val resolver = EnvCredentialResolver { "value" }
        assertNull(resolver.resolve(""))
    }

    @Test
    fun `blank env value returns null`() {
        val resolver = EnvCredentialResolver { "  " }
        assertNull(resolver.resolve("id"))
    }
}
