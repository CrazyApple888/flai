package me.drew.flai.usecase

import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.port.PipelineRepository
import me.drew.flai.infrastructure.pipeline.PipelineValidator

class LoadPipelineUseCase(
    private val repository: PipelineRepository,
    private val validator: PipelineValidator,
) {
    suspend fun invoke(id: PipelineId): Result<Pipeline> = runCatching {
        val pipeline = repository.load(id)
        validator.validate(pipeline)
        pipeline
    }
}
