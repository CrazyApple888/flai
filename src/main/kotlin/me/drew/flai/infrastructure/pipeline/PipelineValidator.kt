package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.GateId
import me.drew.flai.domain.model.Pipeline

class PipelineValidationException(message: String) : Exception(message)

class PipelineValidator {
    fun validate(pipeline: Pipeline) {
        validateEntryExists(pipeline)
        validateEdgeGatesExist(pipeline)
        validateNoCycles(pipeline)
    }

    private fun validateEntryExists(pipeline: Pipeline) {
        if (pipeline.entryGateId !in pipeline.gates) {
            throw PipelineValidationException("Entry gate '${pipeline.entryGateId.value}' not found")
        }
    }

    private fun validateEdgeGatesExist(pipeline: Pipeline) {
        pipeline.edges.forEach { edge ->
            if (edge.from !in pipeline.gates) {
                throw PipelineValidationException("Edge references unknown gate '${edge.from.value}'")
            }
            if (edge.to !in pipeline.gates) {
                throw PipelineValidationException("Edge references unknown gate '${edge.to.value}'")
            }
        }
    }

    // Kahn's algorithm cycle detection
    private fun validateNoCycles(pipeline: Pipeline) {
        val inDegree = mutableMapOf<GateId, Int>()
        pipeline.gates.keys.forEach { inDegree[it] = 0 }
        pipeline.edges.forEach { edge -> inDegree[edge.to] = (inDegree[edge.to] ?: 0) + 1 }

        val queue = ArrayDeque<GateId>()
        inDegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

        var visited = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            visited++
            pipeline.edges.filter { it.from == node }.forEach { edge ->
                inDegree[edge.to] = inDegree[edge.to]!! - 1
                if (inDegree[edge.to] == 0) queue.add(edge.to)
            }
        }

        if (visited != pipeline.gates.size) {
            throw PipelineValidationException("Pipeline contains a cycle — DAG required")
        }
    }
}
