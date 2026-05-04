package me.drew.flai.infrastructure.executor

import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.OutputGate

class DefaultOutputGateExecutor : GateExecutor<OutputGate> {
    override fun canHandle(gate: Gate) = gate is OutputGate

    override suspend fun execute(gate: OutputGate, context: ExecutionContext): GateResult {
        val outputs = gate.outputMapping.mapValues { (_, contextKey) -> context.get(contextKey) }
        return GateResult.Success(outputs)
    }
}
