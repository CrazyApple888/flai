package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.CancellationException
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
    private val skillLoader: SkillLoader,
) : GateExecutor<LlmGate> {
    override fun canHandle(gate: Gate) = gate is LlmGate

    override suspend fun execute(gate: LlmGate, context: ExecutionContext): GateResult {
        return try {
            val skillBodies: List<String> = skillLoader.load(gate.skills)
            val renderedTemplate: String = renderer.render(gate.promptTemplate, context.snapshot())
            val mergedPrompt: String = buildMergedPrompt(skillBodies, renderedTemplate)
            val resolvedApiKey: String? = gate.endpointConfig.apiKeyVar
                ?.let { varName -> context.get(varName)?.toString() }
            val response = llmClient.complete(gate.endpointConfig, mergedPrompt, resolvedApiKey)
            GateResult.Success(outputs = mapOf("response" to response))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SkillLoadException) {
            GateResult.Failure(e, retryable = false)
        } catch (e: Exception) {
            GateResult.Failure(e, retryable = true)
        }
    }

    private fun buildMergedPrompt(skillBodies: List<String>, renderedTemplate: String): String {
        return if (skillBodies.isEmpty()) {
            renderedTemplate
        } else {
            val parts = skillBodies + if (renderedTemplate.isNotEmpty()) listOf(renderedTemplate) else emptyList()
            parts.joinToString("\n\n")
        }
    }
}
