package me.drew.flai.infrastructure.executor

import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.InputGate

class DefaultInputGateExecutor : GateExecutor<InputGate> {
    override fun canHandle(gate: Gate) = gate is InputGate

    override suspend fun execute(gate: InputGate, context: ExecutionContext): GateResult {
        val missing = gate.inputSchema.filter { field ->
            field.required && field.default == null &&
                context.get(field.name).let { it == null || it.toString().isBlank() }
        }
        if (missing.isNotEmpty()) {
            return GateResult.Failure(
                IllegalArgumentException("Missing required inputs: ${missing.map { it.name }}")
            )
        }
        gate.inputSchema.forEach { field ->
            val value = context.get(field.name)
            if ((value == null || value.toString().isBlank()) && field.default != null) {
                context.set(field.name, field.default)
            }
        }
        return GateResult.Success()
    }
}
