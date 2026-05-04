package me.drew.flai.infrastructure.executor

import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.ToolGate
import me.drew.flai.domain.port.ToolRegistry

class DefaultToolGateExecutor(private val toolRegistry: ToolRegistry) : GateExecutor<ToolGate> {
    override fun canHandle(gate: Gate) = gate is ToolGate

    override suspend fun execute(gate: ToolGate, context: ExecutionContext): GateResult {
        val tool = toolRegistry.get(gate.toolName)
            ?: return GateResult.Failure(
                IllegalArgumentException("Tool '${gate.toolName}' not found. Available: ${toolRegistry.listNames()}")
            )
        return try {
            val inputs = context.resolve(gate.inputMapping)
            val outputs = tool.invoke(inputs, context)
            GateResult.Success(outputs)
        } catch (e: Exception) {
            GateResult.Failure(e)
        }
    }
}
