package me.drew.flai.domain.executor

import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult

interface GateExecutor<G : Gate> {
    fun canHandle(gate: Gate): Boolean
    suspend fun execute(gate: G, context: ExecutionContext): GateResult
    suspend fun execute(gate: G, context: ExecutionContext, report: suspend (GateExecutionReport) -> Unit): GateResult {
        return execute(gate, context)
    }
}

data class GateExecutionReport(
    val toolName: String,
    val round: Int,
    val durationMs: Long,
    val succeeded: Boolean,
)
