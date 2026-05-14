package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.channelFlow
import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.*
import me.drew.flai.domain.service.ExecutionEvent
import me.drew.flai.domain.service.PipelineExecutor

class CoroutinePipelineExecutor(private val executors: List<GateExecutor<*>>) : PipelineExecutor {

    override fun execute(pipeline: Pipeline, inputs: Map<String, Any?>) = channelFlow {
        val context = ExecutionContext(inputs)
        var currentGateId: GateId? = pipeline.entryGateId
        var finalOutputs: Map<String, Any?> = emptyMap()

        try {
            while (currentGateId != null) {
                val gate = pipeline.gates[currentGateId]
                    ?: throw IllegalStateException("Gate '${currentGateId.value}' not found in pipeline")

                send(ExecutionEvent.GateStarted(gate.id.value, gate.label))
                val start = System.currentTimeMillis()

                @Suppress("UNCHECKED_CAST")
                val executor = executors.firstOrNull { it.canHandle(gate) } as? GateExecutor<Gate>
                    ?: throw IllegalStateException("No executor for gate type ${gate::class.simpleName}")

                val result = executor.execute(gate, context)
                val duration = System.currentTimeMillis() - start

                if (gate is OutputGate && result is GateResult.Success) {
                    finalOutputs = result.outputs
                }

                currentGateId = handleResult(gate, result, context, duration, pipeline)
            }
            send(ExecutionEvent.PipelineCompleted(context, finalOutputs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(ExecutionEvent.PipelineFailed(e, context))
        }
    }

    private suspend fun ProducerScope<ExecutionEvent>.handleResult(
        gate: Gate,
        result: GateResult,
        context: ExecutionContext,
        duration: Long,
        pipeline: Pipeline,
    ): GateId? {
        return when (result) {
            is GateResult.Success -> {
                applyAndTrace(gate, context, result.outputs, TraceStatus.SUCCESS, null, duration)
                if (gate is OutputGate) null else pipeline.nextGateId(gate.id, "out")
            }
            is GateResult.Routed -> {
                applyAndTrace(gate, context, result.outputs, TraceStatus.SUCCESS, null, duration)
                pipeline.nextGateId(gate.id, result.port)
            }
            is GateResult.Failure -> {
                val entry = TraceEntry(gate.id, gate.label, TraceStatus.FAILURE, result.message, duration)
                context.trace += entry
                send(ExecutionEvent.GateCompleted(entry))
                throw result.error
            }
        }
    }

    private suspend fun ProducerScope<ExecutionEvent>.applyAndTrace(
        gate: Gate,
        context: ExecutionContext,
        outputs: Map<String, Any?>,
        status: TraceStatus,
        message: String?,
        duration: Long,
    ) {
        val outputMapping: Map<String, String> = when (gate) {
            is LlmGate -> gate.outputMapping
            is ToolGate -> gate.outputMapping
            is BashGate -> gate.outputMapping
            is OutputGate -> gate.outputMapping
            is InputGate,
            is LogicGate,
            is ReadFileGate,
            is WriteFileGate -> emptyMap()
        }
        context.applyOutputs(outputMapping, outputs)
        val entry = TraceEntry(gate.id, gate.label, status, message, duration)
        context.trace += entry
        send(ExecutionEvent.GateCompleted(entry))
    }
}
