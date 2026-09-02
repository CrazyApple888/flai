package me.drew.flai.infrastructure.pipeline

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
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
            ?.listFiles { file -> file.isFile && PipelineFileNames.isPipelineFileName(file.name) }
            ?.map { f -> PipelineId(PipelineFileNames.stripExtension(f.name)) }
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

    /**
     * Emits once per virtual-file-system change that can affect `<project>/.flai`.
     * The listener only inspects event paths and never blocks; the consumer decides
     * when to reload.
     */
    override fun watchChanges(): Flow<Unit> = callbackFlow {
        val connection = project.messageBus.connect()
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val directoryPath = pipelineDir?.absolutePath ?: return
                    val relevant = events.any { event ->
                        pathsOf(event).any { path -> PipelineFileEventFilter.isRelevant(path, directoryPath) }
                    }
                    if (relevant) {
                        trySend(Unit)
                    }
                }
            }
        )
        awaitClose { connection.disconnect() }
    }.conflate()

    /** Every path an event touches — renames and moves affect both the old and the new location. */
    private fun pathsOf(event: VFileEvent): List<String> = when {
        event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME ->
            listOf(event.oldPath, event.newPath)

        event is VFileMoveEvent -> listOf(event.oldPath, event.newPath)

        else -> listOf(event.path)
    }

    private fun findFile(dir: File, id: PipelineId): File? {
        if (!dir.exists()) {
            return null
        }
        return dir.listFiles()?.firstOrNull { f ->
            f.isFile &&
                PipelineFileNames.isPipelineFileName(f.name) &&
                PipelineFileNames.stripExtension(f.name) == id.value
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
