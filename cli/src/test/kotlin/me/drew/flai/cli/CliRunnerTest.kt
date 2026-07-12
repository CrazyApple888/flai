package me.drew.flai.cli

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class CliRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pipelineYaml = """
        id: greet
        name: Greet
        entry: start
        gates:
          start:
            type: input
            schema:
              - name: name
                type: STRING
                required: true
          done:
            type: output
            outputMapping:
              greeting: name
        edges:
          - from: start
            to: done
    """.trimIndent()

    private fun writePipeline(): File {
        val file = tmp.newFile("greet.flai.yaml")
        file.writeText(pipelineYaml)
        return file
    }

    private fun run(options: RunOptions): Triple<Int, String, String> {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val exitCode = runBlocking {
            CliRunner(PrintStream(outBytes), PrintStream(errBytes)) { null }.run(options)
        }
        return Triple(exitCode, outBytes.toString(), errBytes.toString())
    }

    private fun options(
        file: File,
        inputs: Map<String, String> = emptyMap(),
        inputsJsonFile: String? = null,
        format: OutputFormat = OutputFormat.TEXT,
    ) = RunOptions(
        pipelineFile = file.absolutePath,
        inputs = inputs,
        inputsJsonFile = inputsJsonFile,
        workdir = null,
        format = format,
        quiet = false,
    )

    @Test
    fun `successful run prints outputs and exits 0`() {
        val (exitCode, out, err) = run(options(writePipeline(), inputs = mapOf("name" to "World")))
        assertEquals(err, 0, exitCode)
        assertTrue(out, out.contains("greeting"))
        assertTrue(out, out.contains("World"))
    }

    @Test
    fun `json format prints machine readable outputs`() {
        val (exitCode, out, _) = run(
            options(writePipeline(), inputs = mapOf("name" to "CI"), format = OutputFormat.JSON)
        )
        assertEquals(0, exitCode)
        @Suppress("UNCHECKED_CAST")
        val parsed = Gson().fromJson(out, Map::class.java) as Map<String, Any?>
        assertEquals("CI", parsed["greeting"])
    }

    @Test
    fun `missing required input exits 1`() {
        val (exitCode, _, err) = run(options(writePipeline()))
        assertEquals(1, exitCode)
        assertTrue(err, err.contains("name"))
    }

    @Test
    fun `inputs json file is merged and explicit input wins`() {
        val jsonFile = tmp.newFile("inputs.json")
        jsonFile.writeText("""{"name": "FromJson"}""")
        val (exitCode, out, _) = run(
            options(writePipeline(), inputs = mapOf("name" to "Explicit"), inputsJsonFile = jsonFile.absolutePath)
        )
        assertEquals(0, exitCode)
        assertTrue(out, out.contains("Explicit"))
    }

    @Test
    fun `missing pipeline file exits 1`() {
        val (exitCode, _, err) = run(options(File(tmp.root, "absent.flai.yaml")))
        assertEquals(1, exitCode)
        assertTrue(err, err.contains("absent.flai.yaml"))
    }
}
