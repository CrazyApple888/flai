package me.drew.flai.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.port.PipelineRepository
import me.drew.flai.domain.service.ExecutionEvent
import me.drew.flai.domain.service.PipelineExecutor
import me.drew.flai.infrastructure.pipeline.PipelineValidator

class RunPipelineUseCase(
    private val repository: PipelineRepository,
    private val executor: PipelineExecutor,
    private val validator: PipelineValidator,
) {
    fun invoke(id: PipelineId, inputs: Map<String, Any?> = emptyMap()): Flow<ExecutionEvent> = flow {
        val pipeline = repository.load(id)
        validator.validate(pipeline)
        executor.execute(pipeline, inputs).collect { emit(it) }
    }
}
