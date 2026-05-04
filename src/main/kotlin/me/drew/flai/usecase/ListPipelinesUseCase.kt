package me.drew.flai.usecase

import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.port.PipelineRepository

class ListPipelinesUseCase(private val repository: PipelineRepository) {
    suspend fun invoke(): List<PipelineId> = repository.listAll()
}
