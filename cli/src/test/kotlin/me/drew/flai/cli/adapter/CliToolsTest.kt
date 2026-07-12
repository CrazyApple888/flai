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
        assertEquals("hello", result["content"])
    }

    @Test
    fun `file read tool reports missing file`() = runBlocking {
        val tool = CliFileReadTool(tmp.root)
        val result = tool.invoke(mapOf("path" to "absent.txt"), ExecutionContext())
        assertEquals("File not found: absent.txt", result["error"])
    }

    @Test
    fun `run command tool executes in workdir`() = runBlocking {
        tmp.newFile("marker.txt")
        val tool = CliRunCommandTool(tmp.root)
        val result = tool.invoke(mapOf("command" to "ls"), ExecutionContext())
        assertEquals(true, result["success"])
        assertEquals(true, (result["output"] as String).contains("marker.txt"))
    }

    @Test
    fun `run command tool reports failure exit code`() = runBlocking {
        val tool = CliRunCommandTool(tmp.root)
        val result = tool.invoke(mapOf("command" to "exit 3"), ExecutionContext())
        assertEquals(false, result["success"])
        assertEquals(3, result["exitCode"])
    }
}
