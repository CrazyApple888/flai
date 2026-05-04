package me.drew.flai.infrastructure.tool

import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool

class RunCommandTool(private val project: Project) : Tool {
    override val name = "ide.runCommand"
    override val description = "Run a shell command in the project directory"

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): Map<String, Any?> {
        val command = inputs["command"]?.toString()
            ?: return mapOf("error" to "Missing 'command' input")
        val workDir = inputs["workDir"]?.toString() ?: project.basePath ?: "."

        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("/bin/sh", "-c", command)
                    .directory(java.io.File(workDir))
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                mapOf("output" to output, "exitCode" to exitCode, "success" to (exitCode == 0))
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: "Unknown error"), "success" to false)
            }
        }
    }
}
