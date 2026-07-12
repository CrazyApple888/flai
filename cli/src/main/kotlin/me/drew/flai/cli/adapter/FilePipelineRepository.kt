package me.drew.flai.cli.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.port.PipelineLoadException
import me.drew.flai.domain.port.PipelineRepository
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import java.io.File

class FilePipelineRepository(
    private val file: File,
    private val parser: YamlPipelineParser,
) : PipelineRepository {

    suspend fun load(): Pipeline = withContext(Dispatchers.IO) {
        if (!file.isFile) {
            throw PipelineLoadException("Pipeline file not found: ${file.absolutePath}")
        }
        parser.parse(file.readText())
    }

    override suspend fun listAll(): List<PipelineId> = listOf(load().id)

    override suspend fun load(id: PipelineId): Pipeline = load()

    override fun watchAll(): Flow<Pipeline> = emptyFlow()
}
