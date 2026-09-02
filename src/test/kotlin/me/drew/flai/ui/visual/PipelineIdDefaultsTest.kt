package me.drew.flai.ui.visual

import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineIdDefaultsTest {

    @Test
    fun `strips flai yaml extension`() {
        assertEquals("my-flow", defaultPipelineIdFor("my-flow.flai.yaml"))
    }

    @Test
    fun `strips flai yml extension`() {
        assertEquals("my-flow", defaultPipelineIdFor("my-flow.flai.yml"))
    }

    @Test
    fun `strips bare flai extension`() {
        assertEquals("my-flow", defaultPipelineIdFor("my-flow.flai"))
    }

    @Test
    fun `strips plain yaml and yml extensions`() {
        assertEquals("a", defaultPipelineIdFor("a.yaml"))
        assertEquals("b", defaultPipelineIdFor("b.yml"))
    }

    @Test
    fun `lowercases and replaces unsafe characters with dashes`() {
        assertEquals("my-flow-v2", defaultPipelineIdFor("My Flow (v2).flai.yaml"))
    }

    @Test
    fun `collapses repeated dashes and trims edges`() {
        assertEquals("a-b", defaultPipelineIdFor("--a--b--.flai.yaml"))
        assertEquals("a-b", defaultPipelineIdFor("  a  b  .yaml"))
    }

    @Test
    fun `keeps underscores`() {
        assertEquals("my_flow", defaultPipelineIdFor("my_flow.flai.yaml"))
    }

    @Test
    fun `falls back to pipeline when nothing is left`() {
        assertEquals("pipeline", defaultPipelineIdFor(".flai.yaml"))
        assertEquals("pipeline", defaultPipelineIdFor("!!!.yaml"))
        assertEquals("pipeline", defaultPipelineIdFor(""))
    }
}
