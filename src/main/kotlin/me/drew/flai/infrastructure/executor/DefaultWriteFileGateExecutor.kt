package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.model.WriteFileGate
import me.drew.flai.domain.model.WriteMode
import me.drew.flai.domain.port.TemplateRenderer
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DefaultWriteFileGateExecutor(
    private val projectRoot: String,
    private val renderer: TemplateRenderer,
) : GateExecutor<WriteFileGate> {

    private val templateVarPattern = Regex("""\{\{(\w+)\}\}""")

    override fun canHandle(gate: Gate): Boolean = gate is WriteFileGate

    override suspend fun execute(gate: WriteFileGate, context: ExecutionContext): GateResult {
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

        val canonicalRoot: Path = try {
            Path.of(projectRoot).toRealPath()
        } catch (e: IOException) {
            return GateResult.Failure(IOException("Cannot resolve project root: ${e.message}", e))
        }
        val rawCandidatePath: Path = file.toPath().normalize().toAbsolutePath()
        val candidatePath: Path = try {
            if (file.exists()) rawCandidatePath.toRealPath()
            else {
                val parent = file.parentFile
                if (parent != null && parent.exists()) {
                    parent.toPath().toRealPath().resolve(file.name)
                } else rawCandidatePath
            }
        } catch (_: IOException) {
            rawCandidatePath
        }

        if (!candidatePath.startsWith(canonicalRoot)) {
            return GateResult.Failure(
                IllegalArgumentException("Path resolves outside project root: ${file.path}")
            )
        }

        val rawValue = context.get(gate.contentKey)
        if (rawValue == null) {
            return GateResult.Failure(
                IllegalArgumentException("Context variable '${gate.contentKey}' not found")
            )
        }
        if (rawValue !is String) {
            return GateResult.Failure(
                IllegalArgumentException(
                    "Context variable '${gate.contentKey}' is not a String (actual: ${rawValue::class.simpleName})"
                )
            )
        }
        val content: String = rawValue

        return withContext(Dispatchers.IO) {
            try {
                file.parentFile?.mkdirs()

                when (gate.mode) {
                    WriteMode.OVERWRITE -> Files.writeString(
                        candidatePath, content, Charsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                    )
                    WriteMode.APPEND -> Files.writeString(
                        candidatePath, content, Charsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND
                    )
                    WriteMode.FAIL_IF_EXISTS -> {
                        if (file.exists()) {
                            return@withContext GateResult.Failure(
                                IllegalStateException("File already exists: ${candidatePath}")
                            )
                        }
                        Files.writeString(
                            candidatePath, content, Charsets.UTF_8,
                            StandardOpenOption.CREATE_NEW
                        )
                    }
                }

                GateResult.Success(mapOf("writtenPath" to candidatePath.toString()))
            } catch (e: IOException) {
                GateResult.Failure(IOException("Cannot write file ${candidatePath}: ${e.message}", e))
            } catch (e: SecurityException) {
                GateResult.Failure(SecurityException("Cannot write file ${candidatePath}: ${e.message}", e))
            }
        }
    }
}
