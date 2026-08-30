package me.drew.flai.infrastructure.tool

import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool
import me.drew.flai.domain.port.ToolInputSchema
import me.drew.flai.domain.port.ToolSchemaProperty
import me.drew.flai.domain.port.ToolSchemaType
import me.drew.flai.domain.port.ToolResult

class RunCommandTool(private val project: Project) : Tool {
    override val name = "ide.runCommand"
    override val description = "Run a shell command in the project directory"
    override val inputSchema = ToolInputSchema(
        properties = mapOf(
            "command" to ToolSchemaProperty(ToolSchemaType.STRING, "Shell command"),
            "workDir" to ToolSchemaProperty(ToolSchemaType.STRING, "Working directory"),
        ),
        required = listOf("command"),
    )

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): ToolResult {
        val command = inputs["command"]?.toString()
            ?: return ToolResult(mapOf("error" to "Missing 'command' input"), isError = true)
        val workDir = inputs["workDir"]?.toString() ?: project.basePath ?: "."

        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("/bin/sh", "-c", command)
                    .directory(java.io.File(workDir))
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                ToolResult(
                    mapOf("output" to output, "exitCode" to exitCode, "success" to (exitCode == 0)),
                    isError = exitCode != 0,
                )
            } catch (e: Exception) {
                ToolResult(mapOf("error" to (e.message ?: "Unknown error"), "success" to false), isError = true)
            }
        }
    }
}
