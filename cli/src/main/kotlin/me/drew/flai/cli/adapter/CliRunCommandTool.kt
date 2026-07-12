package me.drew.flai.cli.adapter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.port.Tool
import java.io.File

class CliRunCommandTool(private val workdir: File) : Tool {
    override val name = "ide.runCommand"
    override val description = "Run a shell command in the working directory"

    override suspend fun invoke(inputs: Map<String, Any?>, context: ExecutionContext): Map<String, Any?> {
        val command = inputs["command"]?.toString()
            ?: return mapOf("error" to "Missing 'command' input")
        val directory = inputs["workDir"]?.toString()?.let { File(it) } ?: workdir

        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("/bin/sh", "-c", command)
                    .directory(directory)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                mapOf("output" to output, "exitCode" to exitCode, "success" to (exitCode == 0))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: "Unknown error"), "success" to false)
            }
        }
    }
}
