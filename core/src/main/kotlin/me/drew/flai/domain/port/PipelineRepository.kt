package me.drew.flai.domain.port

import kotlinx.coroutines.flow.Flow
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineId

interface PipelineRepository {
    suspend fun listAll(): List<PipelineId>
    suspend fun load(id: PipelineId): Pipeline

    /**
     * Emits whenever the pipeline files backing this repository may have changed
     * (created, deleted, renamed, moved or saved). Carries no payload: the consumer
     * reloads the pipelines it needs. Implementations that cannot observe changes
     * return an empty flow.
     */
    fun watchChanges(): Flow<Unit>
}

class PipelineLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
