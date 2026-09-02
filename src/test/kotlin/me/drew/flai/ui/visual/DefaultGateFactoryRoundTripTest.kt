package me.drew.flai.ui.visual

import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import me.drew.flai.infrastructure.pipeline.YamlPipelineSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A gate dropped from the palette must reach the YAML file before it is configured: the model has
 * to stay structurally valid and its serialised form has to parse back.
 */
class DefaultGateFactoryRoundTripTest {

    private val serializer = YamlPipelineSerializer()
    private val parser = YamlPipelineParser()

    @Test
    fun `every palette gate type round-trips straight after it is dropped`() {
        for (gateType in GATE_TYPES) {
            val gateId = DefaultGateFactory.newGateId(gateType)
            val gate = requireNotNull(DefaultGateFactory.create(gateType, gateId)) {
                "No default gate for palette type '$gateType'"
            }
            val model = VisualPipelineModel()
            model.setPipelineMetadata("test", "Test")
            model.addNode(gate, 0, 0)

            val structure = VisualPipelineValidator.validateStructure(model)
            assertTrue(
                "Unconfigured '$gateType' gate is not structurally valid: ${structure.errors}",
                structure.isValid,
            )

            val yaml = serializer.serialize(model.toPipeline())
            val parsed = parser.parse(yaml)
            assertEquals(
                "Round-trip lost the '$gateType' gate. YAML:\n$yaml",
                setOf(gateId),
                parsed.gates.keys,
            )
            assertEquals(gateId, parsed.entryGateId)
        }
    }

    @Test
    fun `unknown palette gate type has no default gate`() {
        assertEquals(null, DefaultGateFactory.create("nope", DefaultGateFactory.newGateId("nope")))
    }

    @Test
    fun `generated gate id keeps the full timestamp`() {
        assertEquals("llm_1700000000000", DefaultGateFactory.newGateId("llm", 1_700_000_000_000L).value)
    }
}
