package me.drew.flai.cli.adapter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool
import me.drew.flai.domain.port.ToolInputSchema
import me.drew.flai.domain.port.ToolSchemaProperty
import me.drew.flai.domain.port.ToolSchemaType
import me.drew.flai.domain.port.ToolResult
import java.io.File

class CliFileReadTool(private val workdir: File) : Tool {
    override val name = "ide.readFile"
    override val description = "Read file content by path (absolute or relative to the working directory)"
    override val inputSchema = ToolInputSchema(
        properties = mapOf("path" to ToolSchemaProperty(ToolSchemaType.STRING, "File path")),
        required = listOf("path"),
    )

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val path = inputs["path"]?.toString()
            ?: return ToolResult(mapOf("error" to "Missing 'path' input"), isError = true)

        return withContext(Dispatchers.IO) {
            val base = workdir.canonicalFile
            val raw = File(path)
            val file = (if (raw.isAbsolute) raw else File(base, path)).canonicalFile
            if (!file.toPath().startsWith(base.toPath())) {
                return@withContext ToolResult(mapOf("error" to "Path is outside the working directory"), isError = true)
            }
            if (!file.exists()) {
                ToolResult(mapOf("error" to "File not found: $path", "content" to null), isError = true)
            } else {
                ToolResult(mapOf("content" to file.readText(), "path" to file.absolutePath, "size" to file.length()))
            }
        }
    }
}
