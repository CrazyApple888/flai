package me.drew.flai.infrastructure.executor

import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.port.LlmClient
import me.drew.flai.domain.port.TemplateRenderer

class DefaultLlmGateExecutor(
    private val llmClient: LlmClient,
    private val renderer: TemplateRenderer,
) : GateExecutor<LlmGate> {
    override fun canHandle(gate: Gate) = gate is LlmGate

    override suspend fun execute(gate: LlmGate, context: ExecutionContext): GateResult {
        return try {
            val prompt = renderer.render(gate.promptTemplate, context.snapshot())
            val response = llmClient.complete(gate.endpointConfig, prompt)
            GateResult.Success(outputs = mapOf("response" to response))
        } catch (e: Exception) {
            GateResult.Failure(e, retryable = true)
        }
    }
}
