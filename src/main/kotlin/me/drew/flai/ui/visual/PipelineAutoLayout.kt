package me.drew.flai.ui.visual

import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.Pipeline

data class AutoLayoutResult(val positions: Map<String, Pair<Int, Int>>)

object PipelineAutoLayout {
    const val NODE_W = 140
    const val NODE_H = 60
    const val H_GAP = 60
    const val V_GAP = 30

    /**
     * BFS topological rank from entryGateId.
     * Column = rank; row = position within rank.
     * Grid spacing: NODE_W + H_GAP horizontally, NODE_H + V_GAP vertically.
     */
    fun compute(pipeline: Pipeline): AutoLayoutResult {
        if (pipeline.gates.isEmpty()) {
            return AutoLayoutResult(emptyMap())
        }

        // Build adjacency list
        val adj = mutableMapOf<GateId, MutableList<GateId>>()
        for (gateId in pipeline.gates.keys) {
            adj[gateId] = mutableListOf()
        }
        for (edge in pipeline.edges) {
            adj.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
        }

        // BFS from entry to assign ranks
        val rankMap = mutableMapOf<GateId, Int>()
        val queue = ArrayDeque<GateId>()
        val entry = pipeline.entryGateId
        if (pipeline.gates.containsKey(entry)) {
            rankMap[entry] = 0
            queue.add(entry)
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentRank = rankMap[current] ?: 0
            for (neighbor in (adj[current] ?: emptyList())) {
                if (!rankMap.containsKey(neighbor)) {
                    rankMap[neighbor] = currentRank + 1
                    queue.add(neighbor)
                }
            }
        }

        // Assign rank to any gates not reachable from entry
        var maxRank = rankMap.values.maxOrNull() ?: 0
        for (gateId in pipeline.gates.keys) {
            if (!rankMap.containsKey(gateId)) {
                maxRank++
                rankMap[gateId] = maxRank
            }
        }

        // Group by rank in insertion order
        val byRank = mutableMapOf<Int, MutableList<GateId>>()
        for ((gateId, rank) in rankMap) {
            byRank.getOrPut(rank) { mutableListOf() }.add(gateId)
        }

        // Assign positions
        val positions = mutableMapOf<String, Pair<Int, Int>>()
        for ((rank, gates) in byRank) {
            val x = rank * (NODE_W + H_GAP)
            for ((row, gateId) in gates.withIndex()) {
                val y = row * (NODE_H + V_GAP)
                positions[gateId.value] = Pair(x, y)
            }
        }

        return AutoLayoutResult(positions)
    }
}
