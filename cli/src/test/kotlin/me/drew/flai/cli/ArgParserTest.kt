package me.drew.flai.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgParserTest {

    private fun parseRun(vararg args: String): RunOptions {
        val result = ArgParser.parse(args.toList())
        assertTrue("Expected Run, got $result", result is ParseResult.Run)
        return (result as ParseResult.Run).options
    }

    private fun parseError(vararg args: String): String {
        val result = ArgParser.parse(args.toList())
        assertTrue("Expected Error, got $result", result is ParseResult.Error)
        return (result as ParseResult.Error).message
    }

    @Test
    fun `parses minimal run command`() {
        val options = parseRun("run", "pipe.flai.yaml")
        assertEquals("pipe.flai.yaml", options.pipelineFile)
        assertEquals(emptyMap<String, String>(), options.inputs)
        assertEquals(OutputFormat.TEXT, options.format)
        assertEquals(false, options.quiet)
    }

    @Test
    fun `parses repeated inputs`() {
        val options = parseRun("run", "p.flai.yaml", "--input", "a=1", "--input", "b=x=y")
        assertEquals(mapOf("a" to "1", "b" to "x=y"), options.inputs)
    }

    @Test
    fun `parses all options`() {
        val options = parseRun(
            "run", "p.flai.yaml",
            "--inputs-json", "in.json",
            "--workdir", "/w",
            "--format", "json",
            "--quiet",
        )
        assertEquals("in.json", options.inputsJsonFile)
        assertEquals("/w", options.workdir)
        assertEquals(OutputFormat.JSON, options.format)
        assertEquals(true, options.quiet)
    }

    @Test
    fun `rejects missing command`() {
        assertTrue(parseError().contains("run"))
    }

    @Test
    fun `rejects unknown command`() {
        assertTrue(parseError("walk", "p.yaml").contains("walk"))
    }

    @Test
    fun `rejects missing pipeline file`() {
        assertTrue(parseError("run").contains("pipeline file"))
    }

    @Test
    fun `rejects input without equals`() {
        assertTrue(parseError("run", "p.yaml", "--input", "novalue").contains("key=value"))
    }

    @Test
    fun `rejects unknown option`() {
        assertTrue(parseError("run", "p.yaml", "--frobnicate").contains("--frobnicate"))
    }

    @Test
    fun `rejects unknown format`() {
        assertTrue(parseError("run", "p.yaml", "--format", "xml").contains("xml"))
    }

    @Test
    fun `rejects option missing value`() {
        assertTrue(parseError("run", "p.yaml", "--workdir").contains("--workdir"))
    }
}
