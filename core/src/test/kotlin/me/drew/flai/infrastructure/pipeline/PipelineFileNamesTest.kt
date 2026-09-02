package me.drew.flai.infrastructure.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineFileNamesTest {

    @Test
    fun `accepts supported pipeline file names`() {
        assertTrue(PipelineFileNames.isPipelineFileName("review.flai"))
        assertTrue(PipelineFileNames.isPipelineFileName("review.flai.yaml"))
        assertTrue(PipelineFileNames.isPipelineFileName("review.flai.yml"))
        assertTrue(PipelineFileNames.isPipelineFileName("review.yaml"))
        assertTrue(PipelineFileNames.isPipelineFileName("review.yml"))
    }

    @Test
    fun `rejects lookalike file names`() {
        assertFalse(PipelineFileNames.isPipelineFileName("review.flai.txt"))
        assertFalse(PipelineFileNames.isPipelineFileName("review.yaml.bak"))
        assertFalse(PipelineFileNames.isPipelineFileName("review.yml.bak"))
        assertFalse(PipelineFileNames.isPipelineFileName("review"))
    }

    @Test
    fun `extension matching is case-insensitive`() {
        assertTrue(PipelineFileNames.isPipelineFileName("Foo.FLAI.YAML"))
        assertTrue(PipelineFileNames.isPipelineFileName("foo.Yml"))
        assertEquals("Foo", PipelineFileNames.stripExtension("Foo.FLAI.YAML"))
    }

    @Test
    fun `stripExtension removes the longest matching extension`() {
        assertEquals("review", PipelineFileNames.stripExtension("review.flai.yaml"))
        assertEquals("review", PipelineFileNames.stripExtension("review.flai.yml"))
        assertEquals("review", PipelineFileNames.stripExtension("review.flai"))
        assertEquals("review", PipelineFileNames.stripExtension("review.yaml"))
        assertEquals("review", PipelineFileNames.stripExtension("review.yml"))
        assertEquals("foo.flai", PipelineFileNames.stripExtension("foo.flai.flai.yaml"))
    }

    @Test
    fun `stripExtension returns name unchanged when nothing matches`() {
        assertEquals("review.txt", PipelineFileNames.stripExtension("review.txt"))
        assertEquals("review", PipelineFileNames.stripExtension("review"))
    }

    @Test
    fun `defaultPipelineIdFor strips every supported extension`() {
        assertEquals("my-flow", PipelineFileNames.defaultPipelineIdFor("my-flow.flai.yaml"))
        assertEquals("my-flow", PipelineFileNames.defaultPipelineIdFor("my-flow.flai.yml"))
        assertEquals("my-flow", PipelineFileNames.defaultPipelineIdFor("my-flow.flai"))
        assertEquals("a", PipelineFileNames.defaultPipelineIdFor("a.yaml"))
        assertEquals("b", PipelineFileNames.defaultPipelineIdFor("b.yml"))
    }

    @Test
    fun `defaultPipelineIdFor lowercases and replaces unsafe characters with dashes`() {
        assertEquals("my-flow-v2", PipelineFileNames.defaultPipelineIdFor("My Flow (v2).flai.yaml"))
        assertEquals("foo", PipelineFileNames.defaultPipelineIdFor("Foo.FLAI.YAML"))
    }

    @Test
    fun `defaultPipelineIdFor collapses repeated dashes and trims edges`() {
        assertEquals("a-b", PipelineFileNames.defaultPipelineIdFor("--a--b--.flai.yaml"))
        assertEquals("a-b", PipelineFileNames.defaultPipelineIdFor("  a  b  .yaml"))
    }

    @Test
    fun `defaultPipelineIdFor keeps underscores`() {
        assertEquals("my_flow", PipelineFileNames.defaultPipelineIdFor("my_flow.flai.yaml"))
    }

    @Test
    fun `defaultPipelineIdFor falls back to pipeline when nothing is left`() {
        assertEquals("pipeline", PipelineFileNames.defaultPipelineIdFor(".flai.yaml"))
        assertEquals("pipeline", PipelineFileNames.defaultPipelineIdFor("!!!.yaml"))
        assertEquals("pipeline", PipelineFileNames.defaultPipelineIdFor(""))
    }
}
