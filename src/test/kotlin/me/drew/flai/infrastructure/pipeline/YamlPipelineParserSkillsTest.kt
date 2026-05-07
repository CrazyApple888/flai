package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.port.PipelineLoadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlPipelineParserSkillsTest {

    private val parser = YamlPipelineParser()

    private fun getLlmGate(yaml: String): LlmGate {
        val pipeline = parser.parse(yaml)
        return pipeline.gates[GateId("ask")] as LlmGate
    }

    @Test
    fun `llm gate with valid skills list parses and skills equals declared paths in order`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: ask
            gates:
              ask:
                type: llm
                promptTemplate: "Do it"
                skills:
                  - .flai/skills/skill-a.md
                  - .flai/skills/skill-b.md
                endpoint:
                  url: https://api.anthropic.com/v1/messages
                  credentialId: my-key
                  model: claude-sonnet-4-6
            edges: []
        """.trimIndent()

        val gate = getLlmGate(yaml)

        assertEquals(listOf(".flai/skills/skill-a.md", ".flai/skills/skill-b.md"), gate.skills)
    }

    @Test
    fun `llm gate with empty skills list parses and skills is empty list`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: ask
            gates:
              ask:
                type: llm
                promptTemplate: "Do it"
                skills: []
                endpoint:
                  url: https://api.anthropic.com/v1/messages
                  credentialId: my-key
                  model: claude-sonnet-4-6
            edges: []
        """.trimIndent()

        val gate = getLlmGate(yaml)

        assertEquals(emptyList<String>(), gate.skills)
    }

    @Test
    fun `llm gate with no skills key parses and skills is empty list`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: ask
            gates:
              ask:
                type: llm
                promptTemplate: "Do it"
                endpoint:
                  url: https://api.anthropic.com/v1/messages
                  credentialId: my-key
                  model: claude-sonnet-4-6
            edges: []
        """.trimIndent()

        val gate = getLlmGate(yaml)

        assertEquals(emptyList<String>(), gate.skills)
    }

    @Test
    fun `llm gate with skills as a scalar string throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: ask
            gates:
              ask:
                type: llm
                promptTemplate: "Do it"
                skills: not-a-list
                endpoint:
                  url: https://api.anthropic.com/v1/messages
                  credentialId: my-key
                  model: claude-sonnet-4-6
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `llm gate with skills containing a non-string element throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: ask
            gates:
              ask:
                type: llm
                promptTemplate: "Do it"
                skills:
                  - .flai/skills/valid.md
                  - 42
                endpoint:
                  url: https://api.anthropic.com/v1/messages
                  credentialId: my-key
                  model: claude-sonnet-4-6
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `input gate with skills key throws PipelineLoadException naming the gate and key`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: gate1
            gates:
              gate1:
                type: input
                skills:
                  - skill.md
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention gate id",
            exception!!.message!!.contains("gate1")
        )
        assertTrue(
            "Message should mention 'skills'",
            exception.message!!.contains("skills")
        )
    }

    @Test
    fun `output gate with skills key throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: gate1
            gates:
              gate1:
                type: output
                skills:
                  - skill.md
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `logic gate with skills key throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: gate1
            gates:
              gate1:
                type: logic
                branches:
                  - port: default
                    condition:
                      type: always
                skills:
                  - skill.md
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `tool gate with skills key throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: gate1
            gates:
              gate1:
                type: tool
                tool: ide.readFile
                skills:
                  - skill.md
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `read-file gate with skills key throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: gate1
            gates:
              gate1:
                type: read-file
                path: some/file.txt
                skills:
                  - skill.md
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `write-file gate with skills key throws PipelineLoadException`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: gate1
            gates:
              gate1:
                type: write-file
                path: some/file.txt
                contentKey: content
                skills:
                  - skill.md
            edges: []
        """.trimIndent()

        val exception = try {
            parser.parse(yaml)
            null
        } catch (e: PipelineLoadException) {
            e
        }

        assertTrue("Expected PipelineLoadException", exception != null)
        assertTrue(
            "Message should mention 'skills'",
            exception!!.message!!.contains("skills")
        )
    }

    @Test
    fun `llm gate with no promptTemplate and non-empty skills list parses without error`() {
        val yaml = """
            id: test-pipeline
            name: Test Pipeline
            entry: ask
            gates:
              ask:
                type: llm
                skills:
                  - .flai/skills/persona.md
                endpoint:
                  url: https://api.anthropic.com/v1/messages
                  credentialId: my-key
                  model: claude-sonnet-4-6
            edges: []
        """.trimIndent()

        val gate = getLlmGate(yaml)

        assertEquals("", gate.promptTemplate)
        assertEquals(listOf(".flai/skills/persona.md"), gate.skills)
    }
}
