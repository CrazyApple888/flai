package me.drew.flai.domain.port

import kotlinx.coroutines.flow.Flow
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineId

interface PipelineRepository {
    suspend fun listAll(): List<PipelineId>
    suspend fun load(id: PipelineId): Pipeline
    fun watchAll(): Flow<Pipeline>
}

class PipelineLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
