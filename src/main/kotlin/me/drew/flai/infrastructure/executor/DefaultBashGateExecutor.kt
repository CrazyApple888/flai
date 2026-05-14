package me.drew.flai.infrastructure.executor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.drew.flai.domain.executor.GateExecutor
import me.drew.flai.domain.model.BashGate
import me.drew.flai.domain.model.ExecutionContext
import me.drew.flai.domain.model.Gate
import me.drew.flai.domain.model.GateResult
import me.drew.flai.domain.port.TemplateRenderer
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class DefaultBashGateExecutor(
    private val projectRoot: String,
    private val renderer: TemplateRenderer,
) : GateExecutor<BashGate> {

    private val templateVarPattern = Regex("""\{\{(\w+)\}\}""")
    private val stderrDiagnosticLimit = 2_000

    override fun canHandle(gate: Gate): Boolean = gate is BashGate

    override suspend fun execute(gate: BashGate, context: ExecutionContext): GateResult {
        try {
            val snapshot = context.snapshot()

            val command = renderTemplate(gate, "command", gate.command, snapshot)
            if (command.isBlank()) {
                return GateResult.Failure(IllegalArgumentException("Bash gate '${gate.label}' resolved command is blank"))
            }

            val renderedWorkingDirectory = renderTemplate(gate, "workingDirectory", gate.workingDirectory, snapshot)
            if (renderedWorkingDirectory.isBlank()) {
                return GateResult.Failure(IllegalArgumentException("Bash gate '${gate.label}' resolved workingDirectory is blank"))
            }

            val environment = mutableMapOf<String, String>()
            for ((key, value) in gate.environment) {
                if (key.isBlank()) {
                    return GateResult.Failure(IllegalArgumentException("Bash gate '${gate.label}' has blank environment key"))
                }
                val renderedValue = renderTemplate(gate, "environment.$key", value, snapshot)
                environment[key] = renderedValue
            }

            val workingDirectory = resolveWorkingDirectory(renderedWorkingDirectory)
                ?: return GateResult.Failure(
                    IllegalArgumentException(
                        "Bash gate '${gate.label}' workingDirectory resolves outside project root: $renderedWorkingDirectory"
                    )
                )
            if (!workingDirectory.exists() || !workingDirectory.isDirectory) {
                return GateResult.Failure(
                    IllegalArgumentException("Bash gate '${gate.label}' workingDirectory is not a directory: ${workingDirectory.path}")
                )
            }

            return runProcess(gate, command, workingDirectory, environment)
        } catch (e: MissingTemplateVariableException) {
            return GateResult.Failure(IllegalArgumentException(e.message, e))
        }
    }

    private fun renderTemplate(
        gate: BashGate,
        field: String,
        template: String,
        snapshot: Map<String, Any?>,
    ): String {
        for (match in templateVarPattern.findAll(template)) {
            val varName = match.groupValues[1]
            if (snapshot[varName] == null) {
                throw MissingTemplateVariableException(gate.label, field, varName)
            }
        }
        return renderer.render(template, snapshot)
    }

    private fun resolveWorkingDirectory(renderedWorkingDirectory: String): File? {
        val root = try {
            Path.of(projectRoot).toRealPath()
        } catch (e: IOException) {
            return null
        }
        val requestedFile = File(renderedWorkingDirectory)
        val file = if (requestedFile.isAbsolute) requestedFile else File(projectRoot, renderedWorkingDirectory)
        val rawPath = file.toPath().normalize().toAbsolutePath()
        val candidate = try {
            if (file.exists()) {
                rawPath.toRealPath()
            } else {
                rawPath
            }
        } catch (_: IOException) {
            rawPath
        }
        return if (candidate.startsWith(root)) {
            candidate.toFile()
        } else {
            null
        }
    }

    private suspend fun runProcess(
        gate: BashGate,
        command: String,
        workingDirectory: File,
        environment: Map<String, String>,
    ): GateResult {
        var process: Process? = null
        return try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val builder = ProcessBuilder("/bin/bash", "-lc", command)
                        .directory(workingDirectory)
                    builder.environment().putAll(environment)
                    process = try {
                        builder.start()
                    } catch (e: IOException) {
                        return@coroutineScope GateResult.Failure(
                            IOException("Bash gate '${gate.label}' failed to start Bash: ${e.message}", e)
                        )
                    } catch (e: SecurityException) {
                        return@coroutineScope GateResult.Failure(
                            SecurityException("Bash gate '${gate.label}' failed to start Bash: ${e.message}", e)
                        )
                    }

                    val activeProcess = process ?: return@coroutineScope GateResult.Failure(
                        IllegalStateException("Bash gate '${gate.label}' failed to start process")
                    )
                    val stdoutDeferred = async(Dispatchers.IO) {
                        activeProcess.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                    val stderrDeferred = async(Dispatchers.IO) {
                        activeProcess.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }

                    val exitCode: Int? = withTimeoutOrNull(gate.timeoutSeconds * 1_000L) {
                        waitForExitCode(activeProcess)
                    }
                    if (exitCode == null) {
                        terminate(activeProcess)
                        stdoutDeferred.cancel()
                        stderrDeferred.cancel()
                        return@coroutineScope GateResult.Failure(
                            IllegalStateException(
                                "Bash gate '${gate.label}' timed out after ${gate.timeoutSeconds} seconds"
                            )
                        )
                    }

                    val stdout = stdoutDeferred.await()
                    val stderr = stderrDeferred.await()
                    val outputs = mapOf(
                        "stdout" to stdout,
                        "stderr" to stderr,
                        "exitCode" to exitCode,
                        "success" to (exitCode == 0),
                        "timedOut" to false,
                    )

                    if (exitCode != 0 && gate.failOnNonZeroExit) {
                        return@coroutineScope GateResult.Failure(
                            IllegalStateException(nonZeroMessage(gate, exitCode, stderr))
                        )
                    }

                    GateResult.Success(outputs)
                }
            }
        } catch (e: CancellationException) {
            process?.let { terminate(it) }
            throw e
        } catch (e: Exception) {
            GateResult.Failure(e)
        }
    }

    private fun nonZeroMessage(gate: BashGate, exitCode: Int, stderr: String): String {
        val trimmedStderr = stderr.trim()
        if (trimmedStderr.isEmpty()) {
            return "Bash gate '${gate.label}' failed with exit code $exitCode"
        }
        val diagnostic = if (trimmedStderr.length > stderrDiagnosticLimit) {
            trimmedStderr.take(stderrDiagnosticLimit) + "..."
        } else {
            trimmedStderr
        }
        return "Bash gate '${gate.label}' failed with exit code $exitCode: $diagnostic"
    }

    private suspend fun waitForExitCode(process: Process): Int {
        while (true) {
            if (process.waitFor(50, TimeUnit.MILLISECONDS)) {
                return process.exitValue()
            }
            delay(25)
        }
    }

    private fun terminate(process: Process) {
        process.destroy()
        if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            process.waitFor(250, TimeUnit.MILLISECONDS)
        }
    }

    private class MissingTemplateVariableException(
        gateLabel: String,
        field: String,
        variable: String,
    ) : IllegalArgumentException("Bash gate '$gateLabel' field '$field' references missing variable '$variable'")
}
