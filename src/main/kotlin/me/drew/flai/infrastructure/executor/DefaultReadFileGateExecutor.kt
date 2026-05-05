package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.ReadFileGate
import me.drew.flai.domain.port.TemplateRenderer
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class DefaultReadFileGateExecutor(
    private val projectRoot: String,
    private val renderer: TemplateRenderer,
) : GateExecutor<ReadFileGate> {

    private val templateVarPattern = Regex("""\{\{(\w+)\}\}""")

    override fun canHandle(gate: Gate): Boolean = gate is ReadFileGate

    override suspend fun execute(gate: ReadFileGate, context: ExecutionContext): GateResult {
        val snapshot = context.snapshot()

        for (match in templateVarPattern.findAll(gate.path)) {
            val varName = match.groupValues[1]
            if (snapshot[varName] == null) {
                return GateResult.Failure(
                    IllegalArgumentException("Variable '$varName' not found in context for path template")
                )
            }
        }

        val renderedPath = renderer.render(gate.path, snapshot)
        if (renderedPath.isBlank()) {
            return GateResult.Failure(IllegalArgumentException("Resolved path is empty string"))
        }

        val file = if (File(renderedPath).isAbsolute) File(renderedPath) else File(projectRoot, renderedPath)
        val resolvedPath = file.absolutePath

        if (!file.exists()) {
            return GateResult.Failure(IllegalStateException("File not found: $resolvedPath"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val isBinary = FileInputStream(file).use { fis ->
                    val buffer = ByteArray(8192)
                    val bytesRead = fis.read(buffer)
                    if (bytesRead > 0) buffer.slice(0 until bytesRead).any { it == 0.toByte() } else false
                }
                if (isBinary) {
                    return@withContext GateResult.Failure(
                        IllegalStateException("File appears to be binary: $resolvedPath")
                    )
                }

                val content = file.readText(Charsets.UTF_8)
                GateResult.Success(mapOf(gate.outputKey to content))
            } catch (e: IOException) {
                GateResult.Failure(IOException("Cannot read file $resolvedPath: ${e.message}", e))
            }
        }
    }
}
