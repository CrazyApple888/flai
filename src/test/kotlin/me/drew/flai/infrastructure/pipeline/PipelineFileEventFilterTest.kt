package me.drew.flai.infrastructure.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineFileEventFilterTest {

    private val pipelineDirectory = "/home/user/project/.flai"

    @Test
    fun `pipeline file directly in the pipeline directory is relevant`() {
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/demo.flai.yaml", pipelineDirectory))
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/demo.flai.yml", pipelineDirectory))
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/demo.flai", pipelineDirectory))
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/demo.yaml", pipelineDirectory))
    }

    @Test
    fun `the pipeline directory itself is relevant`() {
        assertTrue(PipelineFileEventFilter.isRelevant(pipelineDirectory, pipelineDirectory))
    }

    @Test
    fun `trailing separators are ignored on both sides`() {
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/", pipelineDirectory))
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/demo.flai.yaml", "$pipelineDirectory/"))
    }

    @Test
    fun `non pipeline file in the pipeline directory is not relevant`() {
        assertFalse(PipelineFileEventFilter.isRelevant("$pipelineDirectory/notes.txt", pipelineDirectory))
        assertFalse(PipelineFileEventFilter.isRelevant("$pipelineDirectory/layout.json", pipelineDirectory))
    }

    @Test
    fun `pipeline file in a nested directory is not relevant`() {
        assertFalse(PipelineFileEventFilter.isRelevant("$pipelineDirectory/nested/demo.flai.yaml", pipelineDirectory))
    }

    @Test
    fun `pipeline file outside the pipeline directory is not relevant`() {
        assertFalse(PipelineFileEventFilter.isRelevant("/home/user/project/demo.flai.yaml", pipelineDirectory))
    }

    @Test
    fun `sibling directory with the same prefix is not relevant`() {
        assertFalse(PipelineFileEventFilter.isRelevant("/home/user/project/.flai-backup/demo.flai.yaml", pipelineDirectory))
        assertFalse(PipelineFileEventFilter.isRelevant("/home/user/project/.flai-backup", pipelineDirectory))
    }

    @Test
    fun `extension matching is case insensitive`() {
        assertTrue(PipelineFileEventFilter.isRelevant("$pipelineDirectory/Demo.FLAI.YAML", pipelineDirectory))
    }

    @Test
    fun `windows separators are normalized`() {
        val windowsDirectory = "C:\\projects\\demo\\.flai"
        assertTrue(PipelineFileEventFilter.isRelevant("C:\\projects\\demo\\.flai\\demo.flai.yaml", windowsDirectory))
        assertTrue(PipelineFileEventFilter.isRelevant("C:/projects/demo/.flai", windowsDirectory))
        assertFalse(PipelineFileEventFilter.isRelevant("C:\\projects\\demo\\demo.flai.yaml", windowsDirectory))
    }

    @Test
    fun `blank paths are not relevant`() {
        assertFalse(PipelineFileEventFilter.isRelevant("", pipelineDirectory))
        assertFalse(PipelineFileEventFilter.isRelevant("$pipelineDirectory/demo.flai.yaml", ""))
    }
}
