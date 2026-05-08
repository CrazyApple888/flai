package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VisualPipelineModelTest {

    private fun makeModel(vararg gates: Gate): VisualPipelineModel {
        val model = VisualPipelineModel()
        model.pipelineId = "test"
        model.pipelineName = "Test"
        var x = 0
        for (gate in gates) {
            val node = model.addNode(gate, x, 0)
            x += 200
        }
        if (gates.isNotEmpty()) {
            model.entryNodeSeq = model.nodes[0].nodeSeq
        }
        model.isDirty = false
        return model
    }

    private fun inputGate(id: String) = InputGate(id = GateId(id), label = id)
    private fun outputGate(id: String) = OutputGate(id = GateId(id), label = id)

    @Test
    fun `renameGateId cascades to edges with that gate in from`() {
        val model = makeModel(inputGate("g1"), outputGate("g2"))
        val g1Seq = model.nodes[0].nodeSeq
        val g2Seq = model.nodes[1].nodeSeq
        model.addEdge(VisualEdge(fromSeq = g1Seq, toSeq = g2Seq))

        val renamed = model.renameGateId(g1Seq, "g1-renamed")
        assertTrue(renamed)
        assertEquals("g1-renamed", model.nodeBySeq(g1Seq)!!.gateId)
        // Edge still references same seqs, not gateIds — no cascade needed in edges
        assertEquals(1, model.edges.size)
        assertEquals(g1Seq, model.edges[0].fromSeq)
    }

    @Test
    fun `renameGateId cascades gate id in toPipeline edges`() {
        val model = makeModel(inputGate("g1"), outputGate("g2"))
        val g1Seq = model.nodes[0].nodeSeq
        val g2Seq = model.nodes[1].nodeSeq
        model.addEdge(VisualEdge(fromSeq = g1Seq, toSeq = g2Seq))

        model.renameGateId(g1Seq, "new-g1")
        val pipeline = model.toPipeline()
        assertEquals("new-g1", pipeline.edges[0].from.value)
    }

    @Test
    fun `renameGateId returns false when newId is empty`() {
        val model = makeModel(inputGate("g1"))
        val seq = model.nodes[0].nodeSeq
        val result = model.renameGateId(seq, "")
        assertFalse(result)
        assertEquals("g1", model.nodeBySeq(seq)!!.gateId)
    }

    @Test
    fun `renameGateId returns false when newId already exists on another gate`() {
        val model = makeModel(inputGate("g1"), outputGate("g2"))
        val g1Seq = model.nodes[0].nodeSeq
        val result = model.renameGateId(g1Seq, "g2")
        assertFalse(result)
        assertEquals("g1", model.nodeBySeq(g1Seq)!!.gateId)
    }

    @Test
    fun `addEdge returns false for duplicate edge`() {
        val model = makeModel(inputGate("g1"), outputGate("g2"))
        val g1Seq = model.nodes[0].nodeSeq
        val g2Seq = model.nodes[1].nodeSeq
        val edge = VisualEdge(fromSeq = g1Seq, toSeq = g2Seq)
        assertTrue(model.addEdge(edge))
        assertFalse(model.addEdge(edge))
        assertEquals(1, model.edges.size)
    }

    @Test
    fun `removeNode removes all incident edges`() {
        val model = makeModel(inputGate("g1"), outputGate("g2"), outputGate("g3"))
        val g1Seq = model.nodes[0].nodeSeq
        val g2Seq = model.nodes[1].nodeSeq
        val g3Seq = model.nodes[2].nodeSeq
        model.addEdge(VisualEdge(fromSeq = g1Seq, toSeq = g2Seq))
        model.addEdge(VisualEdge(fromSeq = g1Seq, toSeq = g3Seq))
        model.addEdge(VisualEdge(fromSeq = g2Seq, toSeq = g3Seq))

        model.removeNode(g1Seq)

        assertEquals(1, model.edges.size)
        assertEquals(g2Seq, model.edges[0].fromSeq)
        assertEquals(g3Seq, model.edges[0].toSeq)
        assertNull(model.nodeBySeq(g1Seq))
    }

    @Test
    fun `toPipeline produces correct PipelineEdge list with resolved gateId strings`() {
        val model = makeModel(inputGate("start"), outputGate("end"))
        val startSeq = model.nodes[0].nodeSeq
        val endSeq = model.nodes[1].nodeSeq
        model.addEdge(VisualEdge(fromSeq = startSeq, fromPort = "out", toSeq = endSeq, toPort = "in"))

        val pipeline = model.toPipeline()

        assertEquals(1, pipeline.edges.size)
        assertEquals("start", pipeline.edges[0].from.value)
        assertEquals("out", pipeline.edges[0].fromPort)
        assertEquals("end", pipeline.edges[0].to.value)
        assertEquals("in", pipeline.edges[0].toPort)
    }

    @Test
    fun `toPipeline uses entry gate from entryNodeSeq`() {
        val model = makeModel(inputGate("start"), outputGate("end"))
        model.entryNodeSeq = model.nodes[0].nodeSeq

        val pipeline = model.toPipeline()
        assertEquals("start", pipeline.entryGateId.value)
    }

    @Test
    fun `addNode sets isDirty`() {
        val model = VisualPipelineModel()
        model.isDirty = false
        model.addNode(inputGate("g1"), 0, 0)
        assertTrue(model.isDirty)
    }

    @Test
    fun `moveNode updates position and sets isDirty`() {
        val model = makeModel(inputGate("g1"))
        val seq = model.nodes[0].nodeSeq
        model.isDirty = false
        model.moveNode(seq, 100, 200)
        assertEquals(100, model.nodeBySeq(seq)!!.x)
        assertEquals(200, model.nodeBySeq(seq)!!.y)
        assertTrue(model.isDirty)
    }

    @Test
    fun `updateGate replaces gate and updates gateId`() {
        val model = makeModel(inputGate("g1"))
        val seq = model.nodes[0].nodeSeq
        model.isDirty = false
        val newGate = outputGate("g1")
        model.updateGate(seq, newGate)
        assertTrue(model.nodeBySeq(seq)!!.gate is OutputGate)
        assertTrue(model.isDirty)
    }

    @Test
    fun `fromPipeline correctly builds model from Pipeline`() {
        val g1 = InputGate(id = GateId("start"), label = "Start")
        val g2 = OutputGate(id = GateId("end"), label = "End")
        val pipeline = Pipeline(
            id = PipelineId("p"),
            name = "P",
            gates = mapOf(GateId("start") to g1, GateId("end") to g2),
            edges = listOf(PipelineEdge(from = GateId("start"), to = GateId("end"))),
            entryGateId = GateId("start"),
        )
        val model = VisualPipelineModel.fromPipeline(pipeline)

        assertEquals("p", model.pipelineId)
        assertEquals(2, model.nodes.size)
        assertEquals(1, model.edges.size)
        assertFalse(model.isDirty)

        val startNode = model.nodeByGateId("start")!!
        assertEquals(model.entryNodeSeq, startNode.nodeSeq)

        val result = model.toPipeline()
        assertEquals("start", result.edges[0].from.value)
        assertEquals("end", result.edges[0].to.value)
    }

    @Test
    fun `removeNode clears entryNodeSeq when entry gate is removed`() {
        val model = makeModel(inputGate("g1"), outputGate("g2"))
        val g1Seq = model.nodes[0].nodeSeq
        model.entryNodeSeq = g1Seq

        model.removeNode(g1Seq)

        assertEquals(-1, model.entryNodeSeq)
    }
}
