package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.ReadFileGate
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultReadFileGateExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectRoot: String
    private lateinit var executor: DefaultReadFileGateExecutor

    @Before
    fun setUp() {
        projectRoot = tempFolder.root.toPath().toRealPath().toString()
        executor = DefaultReadFileGateExecutor(projectRoot, SimpleTemplateRenderer())
    }

    private fun gate(
        path: String,
        outputKey: String = "content",
    ) = ReadFileGate(id = GateId("test-gate"), label = "Test Gate", path = path, outputKey = outputKey)

    private suspend fun run(gate: ReadFileGate, contextVars: Map<String, Any?> = emptyMap()): GateResult {
        val context = ExecutionContext(contextVars)
        return executor.execute(gate, context)
    }

    @Test
    fun `success - existing text file stored under default key`() = runBlocking {
        val file = tempFolder.newFile("hello.txt")
        file.writeText("hello world", Charsets.UTF_8)

        val result = run(gate(file.absolutePath))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        assertEquals("hello world", outputs["content"])
    }

    @Test
    fun `success - outputKey override stores under custom key`() = runBlocking {
        val file = tempFolder.newFile("data.txt")
        file.writeText("some content", Charsets.UTF_8)

        val result = run(gate(file.absolutePath, outputKey = "file_content"))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        assertEquals("some content", outputs["file_content"])
        assertFalse(outputs.containsKey("content"))
    }

    @Test
    fun `success - zero-byte file produces empty string`() = runBlocking {
        val file = tempFolder.newFile("empty.txt")

        val result = run(gate(file.absolutePath))

        assertTrue(result is GateResult.Success)
        assertEquals("", (result as GateResult.Success).outputs["content"])
    }

    @Test
    fun `failure - file does not exist`() = runBlocking {
        val result = run(gate("$projectRoot/nonexistent.txt"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain path", msg.contains("nonexistent.txt"))
    }

    @Test
    fun `failure - binary file with NUL byte`() = runBlocking {
        val file = tempFolder.newFile("binary.bin")
        file.writeBytes(byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x00, 0x57, 0x6F))

        val result = run(gate(file.absolutePath))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain 'binary'", msg.lowercase().contains("binary"))
    }

    @Test
    fun `failure - template variable not in context`() = runBlocking {
        val result = run(gate("{{file_path}}"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain variable name", msg.contains("file_path"))
    }

    @Test
    fun `failure - template variable resolves to empty string`() = runBlocking {
        val result = run(gate("{{file_path}}"), contextVars = mapOf("file_path" to ""))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should mention empty", msg.lowercase().contains("empty"))
    }

    @Test
    fun `success - absolute path is used as-is`() = runBlocking {
        val file = tempFolder.newFile("absolute.txt")
        file.writeText("absolute content", Charsets.UTF_8)

        val result = run(gate(file.absolutePath))

        assertTrue(result is GateResult.Success)
        assertEquals("absolute content", (result as GateResult.Success).outputs["content"])
    }

    @Test
    fun `success - relative path resolved from projectRoot`() = runBlocking {
        val file = File(projectRoot, "relative.txt")
        file.writeText("relative content", Charsets.UTF_8)

        val result = run(gate("relative.txt"))

        assertTrue(result is GateResult.Success)
        assertEquals("relative content", (result as GateResult.Success).outputs["content"])
    }

    @Test
    fun `success - template variable in path resolves to valid file`() = runBlocking {
        val file = tempFolder.newFile("template.txt")
        file.writeText("template content", Charsets.UTF_8)

        val result = run(gate("{{myPath}}"), contextVars = mapOf("myPath" to file.absolutePath))

        assertTrue(result is GateResult.Success)
        assertEquals("template content", (result as GateResult.Success).outputs["content"])
    }
}
