package me.drew.flai.domain.executor

import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult

interface GateExecutor<G : Gate> {
    fun canHandle(gate: Gate): Boolean
    suspend fun execute(gate: G, context: ExecutionContext): GateResult
}
