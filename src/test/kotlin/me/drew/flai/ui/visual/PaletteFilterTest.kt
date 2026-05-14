package me.drew.flai.ui.visual

import org.junit.Assert.*
import org.junit.Test

class PaletteFilterTest {

    private val allTypes = listOf("input", "output", "llm", "logic", "tool", "bash", "read-file", "write-file")

    @Test
    fun `empty query returns all types`() {
        val result = filterGateTypes("", allTypes)
        assertEquals(allTypes, result)
    }

    @Test
    fun `exact match returns single item`() {
        val result = filterGateTypes("llm", allTypes)
        assertEquals(listOf("llm"), result)
    }

    @Test
    fun `partial match returns matching items`() {
        val result = filterGateTypes("put", allTypes)
        // "input" and "output" both contain "put"
        assertTrue(result.contains("input"))
        assertTrue(result.contains("output"))
        assertFalse(result.contains("llm"))
    }

    @Test
    fun `matching is case-insensitive`() {
        val result = filterGateTypes("LLM", allTypes)
        assertEquals(listOf("llm"), result)
    }

    @Test
    fun `no match returns empty list`() {
        val result = filterGateTypes("xyz", allTypes)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `hyphen-containing type matches by partial name`() {
        val result = filterGateTypes("read", allTypes)
        assertEquals(listOf("read-file"), result)
    }

    @Test
    fun `search for bash returns bash gate type`() {
        val result = filterGateTypes("bash", allTypes)
        assertEquals(listOf("bash"), result)
    }

    @Test
    fun `search for file matches both file gate types`() {
        val result = filterGateTypes("file", allTypes)
        assertTrue(result.contains("read-file"))
        assertTrue(result.contains("write-file"))
    }

    @Test
    fun `filter works with empty types list`() {
        val result = filterGateTypes("llm", emptyList())
        assertTrue(result.isEmpty())
    }
}
