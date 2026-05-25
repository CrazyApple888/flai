package me.drew.flai.infrastructure.tool

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool
import java.io.File

class FileReadTool(private val project: Project) : Tool {
    override val name = "ide.readFile"
    override val description = "Read file content by path (absolute or relative to project root)"

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): Map<String, Any?> {
        val path = inputs["path"]?.toString()
            ?: return mapOf("error" to "Missing 'path' input")

        return withContext(Dispatchers.IO) {
            val file = resolveFile(path)
            if (file == null || !file.exists()) {
                mapOf("error" to "File not found: $path", "content" to null)
            } else {
                mapOf("content" to file.readText(), "path" to file.absolutePath, "size" to file.length())
            }
        }
    }

    private fun resolveFile(path: String): File? {
        val f = File(path)
        if (f.isAbsolute) {
            return f
        }
        val base = project.basePath ?: return f
        return File(base, path)
    }
}
