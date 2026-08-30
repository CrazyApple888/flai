package me.drew.flai.cli.adapter

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.ExecutionContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CliToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `file read tool reads relative to workdir`() = runBlocking {
        tmp.newFile("data.txt").writeText("hello")
        val tool = CliFileReadTool(tmp.root)
        val result = tool.invoke(mapOf("path" to "data.txt"), ExecutionContext())
        assertEquals("hello", result.outputs["content"])
        assertEquals(false, result.isError)
    }

    @Test
    fun `file read tool reports missing file`() = runBlocking {
        val tool = CliFileReadTool(tmp.root)
        val result = tool.invoke(mapOf("path" to "absent.txt"), ExecutionContext())
        assertEquals("File not found: absent.txt", result.outputs["error"])
        assertEquals(true, result.isError)
    }

    @Test
    fun `file read tool rejects paths outside workdir`() = runBlocking {
        val tool = CliFileReadTool(tmp.root)
        val result = tool.invoke(mapOf("path" to "../outside.txt"), ExecutionContext())

        assertEquals("Path is outside the working directory", result.outputs["error"])
        assertEquals(true, result.isError)
    }

    @Test
    fun `run command tool executes in workdir`() = runBlocking {
        tmp.newFile("marker.txt")
        val tool = CliRunCommandTool(tmp.root)
        val result = tool.invoke(mapOf("command" to "ls"), ExecutionContext())
        assertEquals(true, result.outputs["success"])
        assertEquals(true, (result.outputs["output"] as String).contains("marker.txt"))
        assertEquals(false, result.isError)
    }

    @Test
    fun `run command tool reports failure exit code`() = runBlocking {
        val tool = CliRunCommandTool(tmp.root)
        val result = tool.invoke(mapOf("command" to "exit 3"), ExecutionContext())
        assertEquals(false, result.outputs["success"])
        assertEquals(3, result.outputs["exitCode"])
        assertEquals(true, result.isError)
    }
}
