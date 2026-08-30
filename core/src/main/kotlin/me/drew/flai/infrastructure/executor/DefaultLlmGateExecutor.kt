package me.drew.flai.infrastructure.executor

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import me.drew.flai.domain.executor.GateExecutionReport
import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.LlmGate
import me.drew.flai.domain.port.LlmClient
import me.drew.flai.domain.port.TemplateRenderer
import me.drew.flai.domain.port.ToolRegistry

class DefaultLlmGateExecutor(
    llmClient: LlmClient,
    toolRegistry: ToolRegistry,
    private val renderer: TemplateRenderer,
    private val skillLoader: SkillLoader,
    gson: Gson = Gson(),
    private val callLoop: LlmToolCallLoop = LlmToolCallLoop(llmClient, toolRegistry, gson),
) : GateExecutor<LlmGate> {
    override fun canHandle(gate: Gate) = gate is LlmGate

    override suspend fun execute(gate: LlmGate, context: ExecutionContext): GateResult {
        return execute(gate, context) { }
    }

    override suspend fun execute(
        gate: LlmGate,
        context: ExecutionContext,
        report: suspend (GateExecutionReport) -> Unit,
    ): GateResult {
        return try {
            val prompt = loadAndRenderPrompt(gate, context)
            GateResult.Success(callLoop.run(gate, prompt, context, report))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SkillLoadException) {
            GateResult.Failure(e, retryable = false)
        } catch (e: Exception) {
            GateResult.Failure(e, retryable = true)
        }
    }

    private suspend fun loadAndRenderPrompt(gate: LlmGate, context: ExecutionContext): String {
        val skillBodies = skillLoader.load(gate.skills)
        val template = renderer.render(gate.promptTemplate, context.snapshot())
        return if (skillBodies.isEmpty()) {
            template
        } else {
            (skillBodies + if (template.isNotEmpty()) listOf(template) else emptyList()).joinToString("\n\n")
        }
    }
}
