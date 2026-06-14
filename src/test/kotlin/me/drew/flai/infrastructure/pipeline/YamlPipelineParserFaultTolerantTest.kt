package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.port.PipelineLoadException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YamlPipelineParserFaultTolerantTest {

    private val parser = YamlPipelineParser()

    @Test
    fun `faultTolerant true is parsed as true`() {
        val yaml = """
            id: p
            name: P
            entry: run
            gates:
              run:
                type: bash
                command: printf hello
                faultTolerant: true
            edges: []
        """.trimIndent()
        val gate = parser.parse(yaml).gates[GateId("run")]!!
        assertTrue(gate.faultTolerant)
    }

    @Test
    fun `faultTolerant false is parsed as false`() {
        val yaml = """
            id: p
            name: P
            entry: run
            gates:
              run:
                type: bash
                command: printf hello
                faultTolerant: false
            edges: []
        """.trimIndent()
        val gate = parser.parse(yaml).gates[GateId("run")]!!
        assertFalse(gate.faultTolerant)
    }

    @Test
    fun `absent faultTolerant defaults to false`() {
        val yaml = """
            id: p
            name: P
            entry: run
            gates:
              run:
                type: bash
                command: printf hello
            edges: []
        """.trimIndent()
        val gate = parser.parse(yaml).gates[GateId("run")]!!
        assertFalse(gate.faultTolerant)
    }

    @Test
    fun `non-boolean faultTolerant raises PipelineLoadException`() {
        val yaml = """
            id: p
            name: P
            entry: run
            gates:
              run:
                type: bash
                command: printf hello
                faultTolerant: maybe
            edges: []
        """.trimIndent()
        try {
            parser.parse(yaml)
            fail("Expected PipelineLoadException")
        } catch (e: PipelineLoadException) {
            assertTrue(e.message?.contains("faultTolerant") == true)
        }
    }
}
