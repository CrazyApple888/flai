package me.drew.flai.cli.adapter

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.port.PipelineLoadException
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePipelineRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val yaml = """
        id: demo
        name: Demo
        entry: start
        gates:
          start:
            type: input
        edges: []
    """.trimIndent()

    @Test
    fun `loads pipeline from file`() = runBlocking {
        val file = tmp.newFile("demo.flai.yaml")
        file.writeText(yaml)
        val pipeline = FilePipelineRepository(file, YamlPipelineParser()).load()
        assertEquals("demo", pipeline.id.value)
        assertEquals("Demo", pipeline.name)
    }

    @Test
    fun `missing file throws PipelineLoadException`() = runBlocking {
        val repository = FilePipelineRepository(File(tmp.root, "nope.flai.yaml"), YamlPipelineParser())
        try {
            repository.load()
            fail("Expected PipelineLoadException")
        } catch (e: PipelineLoadException) {
            assertEquals(true, e.message!!.contains("nope.flai.yaml"))
        }
    }
}
