package me.drew.flai.ui.service

import me.drew.flai.domain.model.PipelineId
import me.drew.flai.ui.model.UiPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class PipelineListReloadTest {

    private fun validPipeline(
        id: String,
        filePath: Path? = Path.of("/project/.flai/$id.flai.yaml"),
    ) = UiPipeline(
        id = PipelineId(id),
        name = id,
        description = "",
        gateCount = 3,
        filePath = filePath,
        inputSpecs = emptyList(),
    )

    // ── toParseErrorEntry ─────────────────────────────────────────────────

    @Test
    fun `parse error entry keeps the file name without its pipeline extension`() {
        val entry = toParseErrorEntry("Broken Pipeline.flai.yaml", null, "bad indentation")
        assertEquals("Broken Pipeline", entry.name)
    }

    @Test
    fun `parse error entry derives its id from the file name`() {
        val entry = toParseErrorEntry("Broken Pipeline.flai.yaml", null, "bad indentation")
        assertEquals(PipelineId("broken-pipeline"), entry.id)
    }

    @Test
    fun `parse error entry carries the parser message`() {
        val entry = toParseErrorEntry("demo.flai.yaml", null, "bad indentation")
        assertEquals("bad indentation", entry.parseError)
    }

    @Test
    fun `parse error entry has no pipeline details`() {
        val entry = toParseErrorEntry("demo.flai.yaml", null, "bad indentation")
        assertEquals("", entry.description)
        assertEquals(0, entry.gateCount)
        assertTrue(entry.inputSpecs.isEmpty())
    }

    @Test
    fun `parse error entry keeps the file path`() {
        val filePath = Path.of("/project/.flai/demo.flai.yaml")
        val entry = toParseErrorEntry("demo.flai.yaml", filePath, "bad indentation")
        assertEquals(filePath, entry.filePath)
    }

    @Test
    fun `parse error entry falls back to a placeholder message`() {
        val entry = toParseErrorEntry("demo.flai.yaml", null, null)
        assertEquals("Unknown parse error", entry.parseError)
    }

    @Test
    fun `entry loaded from a parseable file carries no parse error`() {
        assertNull(validPipeline("demo").parseError)
    }

    // ── reconcileSelection ────────────────────────────────────────────────

    @Test
    fun `no previous selection stays cleared`() {
        assertNull(reconcileSelection(listOf(validPipeline("demo")), null))
    }

    @Test
    fun `selection is restored by pipeline id`() {
        val previous = validPipeline("demo")
        val reloaded = validPipeline("demo").copy(gateCount = 7)
        val result = reconcileSelection(listOf(validPipeline("other"), reloaded), previous)
        assertSame(reloaded, result)
    }

    @Test
    fun `restored selection is the fresh instance, not the stale one`() {
        val previous = validPipeline("demo")
        val reloaded = toParseErrorEntry("demo.flai.yaml", previous.filePath, "now broken")
            .copy(id = previous.id)
        val result = reconcileSelection(listOf(reloaded), previous)
        assertEquals("now broken", result?.parseError)
    }

    @Test
    fun `selection falls back to the same file path when the id changed`() {
        val filePath = Path.of("/project/.flai/demo.flai.yaml")
        val previous = validPipeline("old-id", filePath)
        val reloaded = validPipeline("new-id", filePath)
        assertSame(reloaded, reconcileSelection(listOf(reloaded), previous))
    }

    @Test
    fun `selection is cleared when the row disappeared`() {
        val previous = validPipeline("demo")
        assertNull(reconcileSelection(listOf(validPipeline("other")), previous))
    }

    @Test
    fun `selection is cleared when the list became empty`() {
        assertNull(reconcileSelection(emptyList(), validPipeline("demo")))
    }

    @Test
    fun `null file paths never match each other`() {
        val previous = validPipeline("demo", filePath = null)
        val other = validPipeline("other", filePath = null)
        assertNull(reconcileSelection(listOf(other), previous))
    }
}
