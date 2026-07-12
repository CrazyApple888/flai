package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.GateResult
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DefaultBashGateExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectRoot: String
    private lateinit var executor: DefaultBashGateExecutor

    @Before
    fun setUp() {
        projectRoot = tempFolder.root.toPath().toRealPath().toString()
        executor = DefaultBashGateExecutor(projectRoot, SimpleTemplateRenderer())
    }

    private fun gate(
        command: String,
        workingDirectory: String = ".",
        environment: Map<String, String> = emptyMap(),
        timeoutSeconds: Int = 120,
        failOnNonZeroExit: Boolean = true,
    ) = BashGate(
        id = GateId("bash"),
        label = "Bash",
        command = command,
        workingDirectory = workingDirectory,
        environment = environment,
        timeoutSeconds = timeoutSeconds,
        failOnNonZeroExit = failOnNonZeroExit,
    )

    private suspend fun run(gate: BashGate, contextVars: Map<String, Any?> = emptyMap()): GateResult {
        return executor.execute(gate, ExecutionContext(contextVars))
    }

    @Test
    fun `success - printf captures stdout and status outputs`() = runBlocking {
        val result = run(gate("printf hello"))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        assertEquals("hello", outputs["stdout"])
        assertEquals("", outputs["stderr"])
        assertEquals(0, outputs["exitCode"])
        assertEquals(true, outputs["success"])
        assertEquals(false, outputs["timedOut"])
    }

    @Test
    fun `success - command template renders context variables`() = runBlocking {
        val result = run(gate("printf '{{name}}'"), mapOf("name" to "world"))

        assertTrue(result is GateResult.Success)
        assertEquals("world", (result as GateResult.Success).outputs["stdout"])
    }

    @Test
    fun `failure - missing command template variable fails before start`() = runBlocking {
        val result = run(gate("printf '{{name}}'"))

        assertTrue(result is GateResult.Failure)
        assertTrue((result as GateResult.Failure).message.contains("name"))
    }

    @Test
    fun `success - environment template is passed to process`() = runBlocking {
        val result = run(
            gate(
                command = "printf \"${'$'}FLAI_VALUE\"",
                environment = mapOf("FLAI_VALUE" to "{{value}}"),
            ),
            mapOf("value" to "from-env"),
        )

        assertTrue(result is GateResult.Success)
        assertEquals("from-env", (result as GateResult.Success).outputs["stdout"])
    }

    @Test
    fun `success - workingDirectory relative to project root affects command`() = runBlocking {
        val subdir = File(projectRoot, "subdir")
        subdir.mkdirs()
        File(subdir, "marker.txt").writeText("ok", Charsets.UTF_8)

        val result = run(gate("cat marker.txt", workingDirectory = "subdir"))

        assertTrue(result is GateResult.Success)
        assertEquals("ok", (result as GateResult.Success).outputs["stdout"])
    }

    @Test
    fun `failure - traversal workingDirectory outside project root is rejected`() = runBlocking {
        val result = run(gate("printf nope", workingDirectory = "../outside"))

        assertTrue(result is GateResult.Failure)
        assertTrue((result as GateResult.Failure).message.lowercase().contains("outside"))
    }

    @Test
    fun `failure - absolute workingDirectory outside project root is rejected`() = runBlocking {
        val result = run(gate("printf nope", workingDirectory = "/tmp"))

        assertTrue(result is GateResult.Failure)
        assertTrue((result as GateResult.Failure).message.lowercase().contains("outside"))
    }

    @Test
    fun `success - stderr is captured separately when exit code is zero`() = runBlocking {
        val result = run(gate("printf out; printf err >&2"))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        assertEquals("out", outputs["stdout"])
        assertEquals("err", outputs["stderr"])
    }

    @Test
    fun `failure - non-zero exit fails by default`() = runBlocking {
        val result = run(gate("printf boom >&2; exit 2"))

        assertTrue(result is GateResult.Failure)
        val message = (result as GateResult.Failure).message
        assertTrue(message.contains("exit code 2"))
        assertTrue(message.contains("boom"))
    }

    @Test
    fun `success - non-zero exit succeeds structurally when configured`() = runBlocking {
        val result = run(gate("exit 2", failOnNonZeroExit = false))

        assertTrue(result is GateResult.Success)
        val outputs = (result as GateResult.Success).outputs
        assertEquals(2, outputs["exitCode"])
        assertEquals(false, outputs["success"])
    }

    @Test
    fun `failure - timeout terminates command`() = runBlocking {
        val result = run(gate("sleep 5", timeoutSeconds = 1))

        assertTrue(result is GateResult.Failure)
        assertTrue((result as GateResult.Failure).message.contains("timed out"))
    }

    @Test
    fun `cancellation terminates process and rethrows cancellation`() = runBlocking {
        val deferred = async {
            run(gate("sleep 5", timeoutSeconds = 10))
        }
        delay(200)
        deferred.cancel()

        try {
            deferred.await()
            assertFalse("CancellationException expected", true)
        } catch (_: CancellationException) {
            assertTrue(true)
        }
    }
}
