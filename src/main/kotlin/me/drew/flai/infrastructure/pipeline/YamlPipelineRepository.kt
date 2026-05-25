package me.drew.flai.infrastructure.pipeline

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.Pipeline
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.port.PipelineLoadException
import me.drew.flai.domain.port.PipelineRepository
import java.io.File

class YamlPipelineRepository(
    private val project: Project,
    private val parser: YamlPipelineParser,
) : PipelineRepository {

    private val pipelineDir: File?
        get() = project.basePath?.let { File(it, ".flai") }

    override suspend fun listAll(): List<PipelineId> = withContext(Dispatchers.IO) {
        pipelineDir
            ?.listFiles { f -> f.name.endsWith(".flai.yaml") || f.name.endsWith(".flai") || f.name.endsWith(".yaml") }
            ?.map { f ->
                val name = f.name
                PipelineId(when {
                    name.endsWith(".flai.yaml") -> name.dropLast(".flai.yaml".length)
                    name.endsWith(".flai") -> name.dropLast(".flai".length)
                    name.endsWith(".yaml") -> name.dropLast(".yaml".length)
                    else -> f.nameWithoutExtension
                })
            }
            ?: emptyList()
    }

    override suspend fun load(id: PipelineId): Pipeline = withContext(Dispatchers.IO) {
        val dir = pipelineDir ?: throw PipelineLoadException("Project has no base path")
        val file = findFile(dir, id) ?: throw PipelineLoadException("Pipeline '${id.value}' not found in $dir")
        try {
            parser.parse(file.readText())
        } catch (e: PipelineLoadException) {
            throw e
        } catch (e: Exception) {
            throw PipelineLoadException("Failed to parse pipeline '${id.value}': ${e.message}", e)
        }
    }

    override fun watchAll(): Flow<Pipeline> = emptyFlow()

    private fun findFile(dir: File, id: PipelineId): File? {
        if (!dir.exists()) {
            return null
        }
        return dir.listFiles()?.firstOrNull { f ->
            f.nameWithoutExtension == id.value ||
                f.name == "${id.value}.flai.yaml" ||
                f.name == "${id.value}.flai" ||
                f.name == "${id.value}.yaml"
        }
    }

    fun loadFromVirtualFile(vf: VirtualFile): Pipeline {
        return try {
            parser.parse(String(vf.contentsToByteArray()))
        } catch (e: Exception) {
            throw PipelineLoadException("Failed to parse ${vf.name}: ${e.message}", e)
        }
    }

    fun refreshVfs() {
        pipelineDir?.let { dir ->
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)
        }
    }
}
