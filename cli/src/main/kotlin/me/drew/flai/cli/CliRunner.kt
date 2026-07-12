package me.drew.flai.cli

import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import me.drew.flai.cli.adapter.CliFileReadTool
import me.drew.flai.cli.adapter.CliRunCommandTool
import me.drew.flai.cli.adapter.EnvCredentialResolver
import me.drew.flai.cli.adapter.FilePipelineRepository
import me.drew.flai.domain.model.TraceStatus
import me.drew.flai.domain.service.ExecutionEvent
import me.drew.flai.infrastructure.executor.CoroutinePipelineExecutor
import me.drew.flai.infrastructure.executor.DefaultBashGateExecutor
import me.drew.flai.infrastructure.executor.DefaultInputGateExecutor
import me.drew.flai.infrastructure.executor.DefaultLlmGateExecutor
import me.drew.flai.infrastructure.executor.DefaultLogicGateExecutor
import me.drew.flai.infrastructure.executor.DefaultOutputGateExecutor
import me.drew.flai.infrastructure.executor.DefaultReadFileGateExecutor
import me.drew.flai.infrastructure.executor.DefaultToolGateExecutor
import me.drew.flai.infrastructure.executor.DefaultWriteFileGateExecutor
import me.drew.flai.infrastructure.executor.SkillLoader
import me.drew.flai.infrastructure.llm.HttpLlmClient
import me.drew.flai.infrastructure.pipeline.PipelineValidator
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import me.drew.flai.infrastructure.tool.DefaultToolRegistry
import me.drew.flai.usecase.RunPipelineUseCase
import java.io.File
import java.io.PrintStream

class CliRunner(
    private val out: PrintStream,
    private val err: PrintStream,
    private val env: (String) -> String? = System::getenv,
) {
    private val gson = Gson()

    suspend fun run(options: RunOptions): Int {
        return try {
            val pipelineFile = File(options.pipelineFile).absoluteFile
            val workdir = resolveWorkdir(options, pipelineFile)
            val inputs = mergeInputs(options)
            execute(pipelineFile, workdir, inputs, options)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err.println("Error: ${e.message}")
            1
        }
    }

    private suspend fun execute(
        pipelineFile: File,
        workdir: File,
        inputs: Map<String, Any?>,
        options: RunOptions,
    ): Int {
        val repository = FilePipelineRepository(pipelineFile, YamlPipelineParser())
        val toolRegistry = DefaultToolRegistry()
        toolRegistry.register(CliFileReadTool(workdir))
        toolRegistry.register(CliRunCommandTool(workdir))
        val renderer = SimpleTemplateRenderer()
        val llmClient = HttpLlmClient(EnvCredentialResolver(env))
        val skillLoader = SkillLoader(workdir.path)
        val executor = CoroutinePipelineExecutor(
            listOf(
                DefaultInputGateExecutor(),
                DefaultOutputGateExecutor(),
                DefaultLlmGateExecutor(llmClient, renderer, skillLoader),
                DefaultLogicGateExecutor(),
                DefaultToolGateExecutor(toolRegistry),
                DefaultBashGateExecutor(workdir.path, renderer),
                DefaultReadFileGateExecutor(workdir.path, renderer),
                DefaultWriteFileGateExecutor(workdir.path, renderer),
            )
        )
        val useCase = RunPipelineUseCase(repository, executor, PipelineValidator())

        val pipeline = repository.load()
        if (!options.quiet) {
            err.println("Running pipeline '${pipeline.name}' (${pipeline.id.value})")
        }
        var exitCode = 1
        useCase.invoke(pipeline.id, inputs).collect { event ->
            when (event) {
                is ExecutionEvent.GateStarted -> {
                    logEvent(options, "> ${event.gateLabel}")
                }
                is ExecutionEvent.GateCompleted -> {
                    val entry = event.entry
                    val marker = when (entry.status) {
                        TraceStatus.SUCCESS -> "ok"
                        TraceStatus.SKIPPED -> "skipped"
                        TraceStatus.TOLERATED_FAILURE -> "tolerated failure"
                        else -> entry.status.name.lowercase()
                    }
                    val message = entry.message?.let { ": $it" } ?: ""
                    logEvent(options, "  ${entry.gateLabel} [$marker] (${entry.durationMs}ms)$message")
                }
                is ExecutionEvent.PipelineCompleted -> {
                    printOutputs(options, event.outputs)
                    exitCode = 0
                }
                is ExecutionEvent.PipelineFailed -> {
                    err.println("Pipeline failed: ${event.error.message}")
                    exitCode = 1
                }
            }
        }
        return exitCode
    }

    private fun logEvent(options: RunOptions, line: String) {
        if (!options.quiet) {
            err.println(line)
        }
    }

    private fun printOutputs(options: RunOptions, outputs: Map<String, Any?>) {
        when (options.format) {
            OutputFormat.JSON -> {
                out.println(gson.toJson(outputs))
            }
            OutputFormat.TEXT -> {
                outputs.forEach { (key, value) ->
                    out.println("$key = $value")
                }
            }
        }
    }

    private fun resolveWorkdir(options: RunOptions, pipelineFile: File): File {
        val explicit = options.workdir?.let { File(it).absoluteFile }
        if (explicit != null) {
            return explicit
        }
        val parent = pipelineFile.parentFile
            ?: throw IllegalStateException("Pipeline file has no parent directory: ${pipelineFile.path}")
        return if (parent.name == ".flai") {
            parent.parentFile ?: parent
        } else {
            parent
        }
    }

    private fun mergeInputs(options: RunOptions): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        options.inputsJsonFile?.let { path ->
            val file = File(path)
            if (!file.isFile) {
                throw IllegalStateException("Inputs JSON file not found: $path")
            }
            @Suppress("UNCHECKED_CAST")
            val parsed = gson.fromJson(file.readText(), Map::class.java) as? Map<String, Any?>
                ?: throw IllegalStateException("Inputs JSON must be a JSON object: $path")
            merged.putAll(parsed)
        }
        merged.putAll(options.inputs)
        return merged
    }
}
