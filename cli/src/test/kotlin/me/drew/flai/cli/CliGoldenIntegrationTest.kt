package me.drew.flai.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the real flai-cli fat JAR (built by the :cli:fatJar task, path passed via the
 * `flai.cli.jar` system property) against a sample pipeline covering input, bash, logic,
 * write-file, read-file, tool and output gates, and compares stdout to golden files.
 */
class CliGoldenIntegrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class CliResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun cliJar(): File {
        val path = System.getProperty("flai.cli.jar")
            ?: throw IllegalStateException("System property 'flai.cli.jar' not set (see cli/build.gradle.kts)")
        val jar = File(path)
        assertTrue("CLI jar not found at $path", jar.isFile)
        return jar
    }

    private fun resource(name: String): String {
        val stream = javaClass.getResourceAsStream("/integration/$name")
            ?: throw IllegalStateException("Test resource not found: /integration/$name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun writeSamplePipeline(): File {
        val file = File(tmp.root, "sample.flai.yaml")
        file.writeText(resource("sample.flai.yaml"))
        return file
    }

    private fun runCli(vararg args: String): CliResult {
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val command = listOf(javaBin, "-jar", cliJar().absolutePath) + args
        val process = ProcessBuilder(command)
            .directory(tmp.root)
            .start()
        val stdout = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val stderr = process.errorStream.bufferedReader(Charsets.UTF_8).readText()
        assertTrue("CLI did not finish within 60s", process.waitFor(60, TimeUnit.SECONDS))
        return CliResult(process.exitValue(), stdout, stderr)
    }

    @Test
    fun `known name takes write-read-tool branch and matches text golden`() {
        val pipeline = writeSamplePipeline()

        val result = runCli(
            "run", pipeline.absolutePath,
            "--input", "name=World",
            "--workdir", tmp.root.absolutePath,
            "--quiet",
        )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(resource("golden-known.txt"), result.stdout)
        assertEquals("Hello, World!", File(tmp.root, "greeting.txt").readText())
    }

    @Test
    fun `known name matches json golden`() {
        val pipeline = writeSamplePipeline()

        val result = runCli(
            "run", pipeline.absolutePath,
            "--input", "name=World",
            "--workdir", tmp.root.absolutePath,
            "--format", "json",
            "--quiet",
        )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(resource("golden-known.json"), result.stdout)
    }

    @Test
    fun `unknown name takes fallback branch and matches text golden`() {
        val pipeline = writeSamplePipeline()

        val result = runCli(
            "run", pipeline.absolutePath,
            "--input", "name=Stranger",
            "--workdir", tmp.root.absolutePath,
            "--quiet",
        )

        assertEquals(result.stderr, 0, result.exitCode)
        assertEquals(resource("golden-unknown.txt"), result.stdout)
    }
}
