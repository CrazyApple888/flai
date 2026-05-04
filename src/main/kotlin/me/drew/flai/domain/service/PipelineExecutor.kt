package me.drew.flai.domain.service

import kotlinx.coroutines.flow.Flow
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.TraceEntry

interface PipelineExecutor {
    fun execute(pipeline: Pipeline, inputs: Map<String, Any?> = emptyMap()): Flow<ExecutionEvent>
}

sealed class ExecutionEvent {
    data class GateStarted(val gateId: String, val gateLabel: String) : ExecutionEvent()
    data class GateCompleted(val entry: TraceEntry) : ExecutionEvent()
    data class PipelineCompleted(val context: ExecutionContext) : ExecutionEvent()
    data class PipelineFailed(val error: Throwable, val context: ExecutionContext) : ExecutionEvent()
}
