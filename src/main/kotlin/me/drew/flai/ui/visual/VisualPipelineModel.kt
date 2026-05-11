package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*

sealed class UndoableEdit {
    data class NodeAdded(val node: VisualNode) : UndoableEdit()
    data class NodeRemoved(val node: VisualNode, val removedEdges: List<VisualEdge>, val wasEntry: Boolean) : UndoableEdit()
    data class EdgeAdded(val edge: VisualEdge) : UndoableEdit()
    data class EdgeRemoved(val edge: VisualEdge) : UndoableEdit()
}

class VisualPipelineModel {
    var pipelineId: String = ""
        private set
    var pipelineName: String = ""
        private set
    var pipelineDescription: String = ""
        private set
    var entryNodeSeq: Int = -1
        private set

    private val _nodes: MutableList<VisualNode> = mutableListOf()
    private val _edges: MutableList<VisualEdge> = mutableListOf()

    val nodes: List<VisualNode> get() = _nodes
    val edges: List<VisualEdge> get() = _edges

    var isDirty: Boolean = false
        private set

    private val undoStack: ArrayDeque<UndoableEdit> = ArrayDeque()
    private var nextSeq: Int = 0
    fun nextNodeSeq(): Int = nextSeq++

    fun clearDirty() {
        isDirty = false
    }

    fun setEntry(seq: Int) {
        entryNodeSeq = seq
        isDirty = true
    }

    fun nodeBySeq(seq: Int): VisualNode? = _nodes.firstOrNull { it.nodeSeq == seq }

    fun nodeByGateId(id: String): VisualNode? = _nodes.firstOrNull { it.gateId == id }

    fun addNode(gate: Gate, x: Int, y: Int): VisualNode {
        val seq = nextNodeSeq()
        val node = VisualNode(nodeSeq = seq, gateId = gate.id.value, gate = gate, x = x, y = y)
        _nodes.add(node)
        undoStack.addLast(UndoableEdit.NodeAdded(node))
        isDirty = true
        return node
    }

    fun removeNode(seq: Int) {
        val node = nodeBySeq(seq) ?: return
        val removedEdges = _edges.filter { it.fromSeq == seq || it.toSeq == seq }
        val wasEntry = entryNodeSeq == seq
        _nodes.removeAll { it.nodeSeq == seq }
        _edges.removeAll { it.fromSeq == seq || it.toSeq == seq }
        if (wasEntry) {
            entryNodeSeq = -1
        }
        undoStack.addLast(UndoableEdit.NodeRemoved(node, removedEdges, wasEntry))
        isDirty = true
    }

    /** Returns false if newId is empty or already used by another gate. */
    fun renameGateId(seq: Int, newId: String): Boolean {
        if (newId.isEmpty()) return false
        if (_nodes.any { it.nodeSeq != seq && it.gateId == newId }) return false
        val idx = _nodes.indexOfFirst { it.nodeSeq == seq }
        if (idx < 0) return false
        val node = _nodes[idx]
        val rebuiltGate = rebuildGateWithId(node.gate, GateId(newId))
        _nodes[idx] = node.copy(gateId = newId, gate = rebuiltGate)
        isDirty = true
        return true
    }

    /** Returns false if duplicate edge. */
    fun addEdge(edge: VisualEdge): Boolean {
        val duplicate = _edges.any {
            it.fromSeq == edge.fromSeq &&
                it.fromPort == edge.fromPort &&
                it.toSeq == edge.toSeq &&
                it.toPort == edge.toPort
        }
        if (duplicate) return false
        _edges.add(edge)
        undoStack.addLast(UndoableEdit.EdgeAdded(edge))
        isDirty = true
        return true
    }

    fun removeEdge(edge: VisualEdge) {
        val removed = _edges.removeAll {
            it.fromSeq == edge.fromSeq &&
                it.fromPort == edge.fromPort &&
                it.toSeq == edge.toSeq &&
                it.toPort == edge.toPort
        }
        if (removed) {
            undoStack.addLast(UndoableEdit.EdgeRemoved(edge))
            isDirty = true
        }
    }

    fun undo(): Boolean {
        val edit = undoStack.removeLastOrNull() ?: return false
        when (edit) {
            is UndoableEdit.NodeAdded -> {
                _nodes.removeAll { it.nodeSeq == edit.node.nodeSeq }
                _edges.removeAll { it.fromSeq == edit.node.nodeSeq || it.toSeq == edit.node.nodeSeq }
                if (entryNodeSeq == edit.node.nodeSeq) {
                    entryNodeSeq = -1
                }
            }
            is UndoableEdit.NodeRemoved -> {
                _nodes.add(edit.node)
                _edges.addAll(edit.removedEdges)
                if (edit.wasEntry) {
                    entryNodeSeq = edit.node.nodeSeq
                }
            }
            is UndoableEdit.EdgeAdded -> {
                _edges.removeAll {
                    it.fromSeq == edit.edge.fromSeq && it.fromPort == edit.edge.fromPort &&
                        it.toSeq == edit.edge.toSeq && it.toPort == edit.edge.toPort
                }
            }
            is UndoableEdit.EdgeRemoved -> {
                _edges.add(edit.edge)
            }
        }
        isDirty = true
        return true
    }

    fun clearHistory() {
        undoStack.clear()
    }

    fun moveNode(seq: Int, x: Int, y: Int) {
        val idx = _nodes.indexOfFirst { it.nodeSeq == seq }
        if (idx < 0) return
        _nodes[idx] = _nodes[idx].copy(x = x, y = y)
        isDirty = true
    }

    fun updateGate(seq: Int, gate: Gate) {
        val idx = _nodes.indexOfFirst { it.nodeSeq == seq }
        if (idx < 0) return
        _nodes[idx] = _nodes[idx].copy(gate = gate, gateId = gate.id.value)
        isDirty = true
    }

    fun replaceWith(other: VisualPipelineModel) {
        _nodes.clear()
        _nodes.addAll(other._nodes)
        _edges.clear()
        _edges.addAll(other._edges)
        pipelineId = other.pipelineId
        pipelineName = other.pipelineName
        pipelineDescription = other.pipelineDescription
        entryNodeSeq = other.entryNodeSeq
        isDirty = other.isDirty
        nextSeq = other.nextSeq
    }

    fun toPipeline(): Pipeline {
        val gatesMap = _nodes.associate { node ->
            GateId(node.gateId) to node.gate
        }
        val pipelineEdges = _edges.mapNotNull { edge ->
            val fromNode = nodeBySeq(edge.fromSeq) ?: return@mapNotNull null
            val toNode = nodeBySeq(edge.toSeq) ?: return@mapNotNull null
            PipelineEdge(
                from = GateId(fromNode.gateId),
                fromPort = edge.fromPort,
                to = GateId(toNode.gateId),
                toPort = edge.toPort,
            )
        }
        val entryNode = nodeBySeq(entryNodeSeq)
        val entryGateId = GateId(entryNode?.gateId ?: (_nodes.firstOrNull()?.gateId ?: ""))
        return Pipeline(
            id = PipelineId(pipelineId),
            name = pipelineName,
            description = pipelineDescription,
            gates = gatesMap,
            edges = pipelineEdges,
            entryGateId = entryGateId,
        )
    }

    private fun rebuildGateWithId(gate: Gate, newId: GateId): Gate = when (gate) {
        is InputGate -> gate.copy(id = newId)
        is OutputGate -> gate.copy(id = newId)
        is LlmGate -> gate.copy(id = newId)
        is LogicGate -> gate.copy(id = newId)
        is ToolGate -> gate.copy(id = newId)
        is ReadFileGate -> gate.copy(id = newId)
        is WriteFileGate -> gate.copy(id = newId)
    }

    companion object {
        fun fromPipeline(pipeline: Pipeline, positions: Map<String, GatePosition> = emptyMap()): VisualPipelineModel {
            val model = VisualPipelineModel()
            model.pipelineId = pipeline.id.value
            model.pipelineName = pipeline.name
            model.pipelineDescription = pipeline.description

            val autoLayout = PipelineAutoLayout.compute(pipeline)

            for ((gateId, gate) in pipeline.gates) {
                val pos = positions[gateId.value]
                val autoPos = autoLayout.positions[gateId.value]
                val x = pos?.x ?: autoPos?.first ?: 0
                val y = pos?.y ?: autoPos?.second ?: 0
                model.addNode(gate, x, y)
            }
            model.isDirty = false

            // Set entry after adding nodes
            val entryNode = model.nodeByGateId(pipeline.entryGateId.value)
            model.entryNodeSeq = entryNode?.nodeSeq ?: -1

            // Add edges using nodeSeq references
            for (edge in pipeline.edges) {
                val fromNode = model.nodeByGateId(edge.from.value) ?: continue
                val toNode = model.nodeByGateId(edge.to.value) ?: continue
                model.addEdge(VisualEdge(
                    fromSeq = fromNode.nodeSeq,
                    fromPort = edge.fromPort,
                    toSeq = toNode.nodeSeq,
                    toPort = edge.toPort,
                ))
            }
            model.isDirty = false
            model.clearHistory()
            return model
        }
    }
}
