package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PipelineAutoLayoutTest {

    private fun pipeline(vararg edges: Pair<String, String>, entry: String = "g0"): Pipeline {
        val gateIds = (edges.flatMap { listOf(it.first, it.second) } + entry).toSet()
        val gates = gateIds.associateBy(
            { GateId(it) },
            { id -> InputGate(id = GateId(id), label = id) as Gate },
        )
        val pipelineEdges = edges.map { (from, to) ->
            PipelineEdge(from = GateId(from), to = GateId(to))
        }
        return Pipeline(
            id = PipelineId("test"),
            name = "Test",
            gates = gates,
            edges = pipelineEdges,
            entryGateId = GateId(entry),
        )
    }

    @Test
    fun `single gate at origin`() {
        val p = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("g0") to InputGate(id = GateId("g0"), label = "G0")),
            edges = emptyList(),
            entryGateId = GateId("g0"),
        )
        val result = PipelineAutoLayout.compute(p)
        val pos = result.positions["g0"]!!
        assertEquals(0, pos.first)
        assertEquals(0, pos.second)
    }

    @Test
    fun `chain pipeline assigns distinct x positions in order`() {
        val p = pipeline("g0" to "g1", "g1" to "g2", entry = "g0")
        val result = PipelineAutoLayout.compute(p)
        val x0 = result.positions["g0"]!!.first
        val x1 = result.positions["g1"]!!.first
        val x2 = result.positions["g2"]!!.first
        assertTrue("g0 x < g1 x", x0 < x1)
        assertTrue("g1 x < g2 x", x1 < x2)
    }

    @Test
    fun `entry node has rank 0 (leftmost x = 0)`() {
        val p = pipeline("g0" to "g1", "g1" to "g2", entry = "g0")
        val result = PipelineAutoLayout.compute(p)
        assertEquals(0, result.positions["g0"]!!.first)
    }

    @Test
    fun `fork pipeline — no two nodes share the same x and y`() {
        // g0 → g1, g0 → g2 (fork)
        val p = pipeline("g0" to "g1", "g0" to "g2", entry = "g0")
        val result = PipelineAutoLayout.compute(p)
        val positions = result.positions.values.toList()
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                val a = positions[i]
                val b = positions[j]
                assertFalse("Two nodes share same (x,y): $a", a.first == b.first && a.second == b.second)
            }
        }
    }

    @Test
    fun `all gates appear in result`() {
        val p = pipeline("g0" to "g1", "g1" to "g2", entry = "g0")
        val result = PipelineAutoLayout.compute(p)
        assertEquals(3, result.positions.size)
        assertTrue(result.positions.containsKey("g0"))
        assertTrue(result.positions.containsKey("g1"))
        assertTrue(result.positions.containsKey("g2"))
    }

    @Test
    fun `empty pipeline returns empty positions`() {
        val p = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = emptyMap(),
            edges = emptyList(),
            entryGateId = GateId("none"),
        )
        val result = PipelineAutoLayout.compute(p)
        assertTrue(result.positions.isEmpty())
    }

    @Test
    fun `unreachable gates are still placed`() {
        // g0 is entry, g1 is unreachable (no edge from g0)
        val p = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(
                GateId("g0") to InputGate(id = GateId("g0"), label = "G0"),
                GateId("g1") to OutputGate(id = GateId("g1"), label = "G1"),
            ),
            edges = emptyList(),
            entryGateId = GateId("g0"),
        )
        val result = PipelineAutoLayout.compute(p)
        assertEquals(2, result.positions.size)
        assertNotNull(result.positions["g0"])
        assertNotNull(result.positions["g1"])
    }
}
