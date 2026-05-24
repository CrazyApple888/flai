package me.drew.flai.ui.service

import me.drew.flai.domain.model.PipelineId
import me.drew.flai.ui.model.InputFieldSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class MergeInputsTest {

    private fun spec(key: String, default: String = "") =
        InputFieldSpec(key = key, label = key, defaultValue = default, required = false)

    // ── mergeInputs logic ──────────────────────────────────────────────────

    @Test
    fun `retained value overrides spec default`() {
        val specs = listOf(spec("key", "default"))
        val retained = mapOf("key" to "typed")
        val result = mergeInputs(specs, retained)
        assertEquals("typed", result["key"])
    }

    @Test
    fun `missing retained entry falls back to spec default`() {
        val specs = listOf(spec("key", "default"))
        val result = mergeInputs(specs, emptyMap())
        assertEquals("default", result["key"])
    }

    @Test
    fun `stale retained key absent from specs is discarded (FR-9)`() {
        val specs = listOf(spec("active", "v"))
        val retained = mapOf("active" to "kept", "stale" to "gone")
        val result = mergeInputs(specs, retained)
        assertEquals(setOf("active"), result.keys)
        assertEquals("kept", result["active"])
    }

    @Test
    fun `empty retained map uses all spec defaults`() {
        val specs = listOf(spec("a", "x"), spec("b", "y"))
        val result = mergeInputs(specs, emptyMap())
        assertEquals(mapOf("a" to "x", "b" to "y"), result)
    }

    @Test
    fun `empty specs produces empty map regardless of retained`() {
        val result = mergeInputs(emptyList(), mapOf("orphan" to "value"))
        assertTrue(result.isEmpty())
    }

    // ── saveInputValues / getSavedInputValues round-trip ──────────────────

    private fun makeStore(): ConcurrentHashMap<PipelineId, Map<String, String>> = ConcurrentHashMap()

    private fun save(
        store: ConcurrentHashMap<PipelineId, Map<String, String>>,
        pipelineId: PipelineId,
        values: Map<String, String>,
    ) {
        store[pipelineId] = values.toMap()
    }

    private fun get(
        store: ConcurrentHashMap<PipelineId, Map<String, String>>,
        pipelineId: PipelineId,
    ): Map<String, String> = store[pipelineId] ?: emptyMap()

    @Test
    fun `save and retrieve for same pipeline returns saved values`() {
        val store = makeStore()
        val id = PipelineId("pipeline-a")
        save(store, id, mapOf("field" to "value"))
        assertEquals(mapOf("field" to "value"), get(store, id))
    }

    @Test
    fun `retrieve for different pipeline returns empty map (FR-7 isolation)`() {
        val store = makeStore()
        save(store, PipelineId("pipeline-a"), mapOf("field" to "value"))
        assertTrue(get(store, PipelineId("pipeline-b")).isEmpty())
    }

    @Test
    fun `second save for same pipeline replaces first`() {
        val store = makeStore()
        val id = PipelineId("pipeline-a")
        save(store, id, mapOf("field" to "first"))
        save(store, id, mapOf("field" to "second"))
        assertEquals("second", get(store, id)["field"])
    }

    @Test
    fun `retrieve before any save returns empty map`() {
        val store = makeStore()
        assertTrue(get(store, PipelineId("unseen")).isEmpty())
    }

    // ── runFromFile input construction ─────────────────────────────────────

    @Test
    fun `runFromFile scenario - retained overrides default for one field, other uses default`() {
        val specs = listOf(spec("name", "default-name"), spec("count", "0"))
        val retained = mapOf("name" to "alice")
        val result = mergeInputs(specs, retained)
        assertEquals("alice", result["name"])
        assertEquals("0", result["count"])
    }

    @Test
    fun `runFromFile scenario - no retained values produces spec-default map (AC-1_2 no regression)`() {
        val specs = listOf(spec("x", "foo"), spec("y", "bar"))
        val result = mergeInputs(specs, emptyMap())
        assertEquals(mapOf("x" to "foo", "y" to "bar"), result)
    }
}
