package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.WriteFileGate
import me.drew.flai.domain.model.WriteMode
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultWriteFileGateExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectRoot: String
    private lateinit var executor: DefaultWriteFileGateExecutor

    @Before
    fun setUp() {
        projectRoot = tempFolder.root.toPath().toRealPath().toString()
        executor = DefaultWriteFileGateExecutor(projectRoot, SimpleTemplateRenderer())
    }

    private fun gate(
        path: String,
        contentKey: String = "content",
        mode: WriteMode = WriteMode.OVERWRITE,
    ) = WriteFileGate(id = GateId("test-gate"), label = "Test Gate", path = path, contentKey = contentKey, mode = mode)

    private suspend fun run(gate: WriteFileGate, contextVars: Map<String, Any?> = emptyMap()): GateResult {
        val context = ExecutionContext(contextVars)
        return executor.execute(gate, context)
    }

    @Test
    fun `success - overwrite mode writes file and sets writtenPath`() = runBlocking {
        val path = "$projectRoot/output.txt"
        val result = run(gate(path), contextVars = mapOf("content" to "hello"))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        assertTrue(outputs.containsKey("writtenPath"))
        assertEquals("hello", File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `success - overwrite mode on existing file replaces content entirely`() = runBlocking {
        val file = tempFolder.newFile("existing.txt")
        file.writeText("old content", Charsets.UTF_8)

        val result = run(gate(file.absolutePath, mode = WriteMode.OVERWRITE), contextVars = mapOf("content" to "new content"))

        assertTrue(result is GateResult.Success)
        assertEquals("new content", file.readText(Charsets.UTF_8))
    }

    @Test
    fun `success - append mode on existing file appends content`() = runBlocking {
        val file = tempFolder.newFile("append.txt")
        file.writeText("first", Charsets.UTF_8)

        val result = run(gate(file.absolutePath, mode = WriteMode.APPEND), contextVars = mapOf("content" to " second"))

        assertTrue(result is GateResult.Success)
        assertEquals("first second", file.readText(Charsets.UTF_8))
    }

    @Test
    fun `success - append mode on non-existent file creates file`() = runBlocking {
        val path = "$projectRoot/new-append.txt"

        val result = run(gate(path, mode = WriteMode.APPEND), contextVars = mapOf("content" to "created"))

        assertTrue(result is GateResult.Success)
        assertEquals("created", File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `success - fail-if-exists on non-existent file creates file`() = runBlocking {
        val path = "$projectRoot/new-failifexists.txt"

        val result = run(gate(path, mode = WriteMode.FAIL_IF_EXISTS), contextVars = mapOf("content" to "new"))

        assertTrue(result is GateResult.Success)
        assertEquals("new", File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `failure - fail-if-exists on existing file`() = runBlocking {
        val file = tempFolder.newFile("exists.txt")
        file.writeText("existing", Charsets.UTF_8)

        val result = run(gate(file.absolutePath, mode = WriteMode.FAIL_IF_EXISTS), contextVars = mapOf("content" to "new"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain path", msg.contains(file.name))
    }

    @Test
    fun `success - parent dirs do not exist are created`() = runBlocking {
        val path = "$projectRoot/nested/deep/output.txt"

        val result = run(gate(path), contextVars = mapOf("content" to "nested"))

        assertTrue(result is GateResult.Success)
        assertEquals("nested", File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `failure - contentKey not in context`() = runBlocking {
        val path = "$projectRoot/output.txt"

        val result = run(gate(path, contentKey = "missing_key"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain key name", msg.contains("missing_key"))
    }

    @Test
    fun `failure - contentKey value is Int not String`() = runBlocking {
        val path = "$projectRoot/output.txt"

        val result = run(gate(path, contentKey = "num"), contextVars = mapOf("num" to 42))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain key name", msg.contains("num"))
        assertTrue("message should contain type name", msg.contains("Int"))
    }

    @Test
    fun `failure - path with traversal escaping project root`() = runBlocking {
        val result = run(gate("../../outside.txt"), contextVars = mapOf("content" to "data"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should mention path scope", msg.lowercase().contains("outside"))
    }

    @Test
    fun `failure - absolute path outside project root`() = runBlocking {
        val outsidePath = "/tmp/outside-project.txt"

        val result = run(gate(outsidePath), contextVars = mapOf("content" to "data"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain path", msg.contains("outside"))
    }

    @Test
    fun `success - absolute path inside project root`() = runBlocking {
        val path = "$projectRoot/inside.txt"

        val result = run(gate(path), contextVars = mapOf("content" to "inside content"))

        assertTrue(result is GateResult.Success)
        assertEquals("inside content", File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `failure - template variable not in context`() = runBlocking {
        val result = run(gate("{{run_id}}/output.txt"), contextVars = mapOf("content" to "data"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should contain variable name", msg.contains("run_id"))
    }

    @Test
    fun `failure - template variable resolves to empty string`() = runBlocking {
        val result = run(gate("{{run_id}}"), contextVars = mapOf("run_id" to "", "content" to "data"))

        assertTrue(result is GateResult.Failure)
        val msg = (result as GateResult.Failure).message
        assertTrue("message should mention empty", msg.lowercase().contains("empty"))
    }

    @Test
    fun `success - empty string value for contentKey writes zero-byte file`() = runBlocking {
        val path = "$projectRoot/empty.txt"

        val result = run(gate(path), contextVars = mapOf("content" to ""))

        assertTrue(result is GateResult.Success)
        assertEquals("", File(path).readText(Charsets.UTF_8))
    }

    @Test
    fun `success - writtenPath in outputs equals canonicalized absolute path`() = runBlocking {
        val path = "$projectRoot/check-path.txt"

        val result = run(gate(path), contextVars = mapOf("content" to "data"))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        val writtenPath = outputs["writtenPath"] as String
        assertEquals(File(path).toPath().normalize().toAbsolutePath().toString(), writtenPath)
    }
}
