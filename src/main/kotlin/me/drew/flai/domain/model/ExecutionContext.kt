package me.drew.flai.domain.model

import java.util.concurrent.ConcurrentHashMap

class ExecutionContext(initialInputs: Map<String, Any?> = emptyMap()) {
    private val variables: ConcurrentHashMap<String, Any?> = ConcurrentHashMap(initialInputs)
    val trace: MutableList<TraceEntry> = mutableListOf()

    fun get(key: String): Any? = variables[key]
    fun set(key: String, value: Any?) { variables[key] = value }
    fun setAll(map: Map<String, Any?>) { variables.putAll(map) }
    fun snapshot(): Map<String, Any?> = HashMap(variables)

    fun resolve(mapping: Map<String, String>): Map<String, Any?> =
        mapping.mapValues { (_, contextKey) -> variables[contextKey] }

    fun applyOutputs(outputMapping: Map<String, String>, outputs: Map<String, Any?>) {
        outputMapping.forEach { (outputKey, contextKey) ->
            val value = outputs[outputKey]
            if (value != null) variables[contextKey] = value
        }
        // If outputMapping is empty but outputs has values, store them directly
        if (outputMapping.isEmpty()) {
            variables.putAll(outputs)
        }
    }
}

data class TraceEntry(
    val gateId: GateId,
    val gateLabel: String,
    val status: TraceStatus,
    val message: String? = null,
    val durationMs: Long = 0,
)

enum class TraceStatus { STARTED, SUCCESS, FAILURE, SKIPPED }
