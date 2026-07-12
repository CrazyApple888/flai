package me.drew.flai.cli.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool
import java.io.File

class CliFileReadTool(private val workdir: File) : Tool {
    override val name = "ide.readFile"
    override val description = "Read file content by path (absolute or relative to the working directory)"

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): Map<String, Any?> {
        val path = inputs["path"]?.toString()
            ?: return mapOf("error" to "Missing 'path' input")

        return withContext(Dispatchers.IO) {
            val raw = File(path)
            val file = if (raw.isAbsolute) {
                raw
            } else {
                File(workdir, path)
            }
            if (!file.exists()) {
                mapOf("error" to "File not found: $path", "content" to null)
            } else {
                mapOf("content" to file.readText(), "path" to file.absolutePath, "size" to file.length())
            }
        }
    }
}
