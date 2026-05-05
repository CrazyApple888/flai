package me.drew.flai.ui.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.model.TraceStatus
import me.drew.flai.domain.service.ExecutionEvent
import me.drew.flai.infrastructure.executor.*
import me.drew.flai.infrastructure.llm.HttpLlmClient
import me.drew.flai.infrastructure.pipeline.PipelineValidator
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import me.drew.flai.infrastructure.pipeline.YamlPipelineRepository
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import me.drew.flai.infrastructure.tool.IdeToolRegistry
import me.drew.flai.ui.model.*
import me.drew.flai.usecase.ListPipelinesUseCase
import me.drew.flai.usecase.RunPipelineUseCase
import java.io.File
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class FlaiPipelineUiService(private val project: Project) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val toolRegistry = IdeToolRegistry()

    private val llmClient = HttpLlmClient()
    private val renderer = SimpleTemplateRenderer()
    private val parser = YamlPipelineParser()
    private val validator = PipelineValidator()
    val repository = YamlPipelineRepository(project, parser)

    private val pipelineExecutor = CoroutinePipelineExecutor(
        listOf(
            DefaultInputGateExecutor(),
            DefaultOutputGateExecutor(),
            DefaultLlmGateExecutor(llmClient, renderer),
            DefaultLogicGateExecutor(),
            DefaultToolGateExecutor(toolRegistry),
            DefaultReadFileGateExecutor(project.basePath!!, renderer),
            DefaultWriteFileGateExecutor(project.basePath!!, renderer),
        )
    )

    private val runUseCase = RunPipelineUseCase(repository, pipelineExecutor)
    private val listUseCase = ListPipelinesUseCase(repository)

    private val _pipelines = MutableStateFlow<List<UiPipeline>>(emptyList())
    val pipelines: StateFlow<List<UiPipeline>> = _pipelines.asStateFlow()

    private val _selectedPipeline = MutableStateFlow<UiPipeline?>(null)
    val selectedPipeline: StateFlow<UiPipeline?> = _selectedPipeline.asStateFlow()

    private val _executionState = MutableStateFlow<ExecutionUiState>(ExecutionUiState.Idle)
    val executionState: StateFlow<ExecutionUiState> = _executionState.asStateFlow()

    private val _logRows = MutableStateFlow<List<GateRow>>(emptyList())
    val logRows: StateFlow<List<GateRow>> = _logRows.asStateFlow()

    private var runningJob: Job? = null

    fun refresh() {
        serviceScope.launch {
            try {
                val uiPipelines = loadAllWithPaths()
                _pipelines.value = uiPipelines
            } catch (_: Exception) {
            }
        }
    }

    fun selectPipeline(pipeline: UiPipeline) {
        _selectedPipeline.value = pipeline
    }

    fun run(pipeline: UiPipeline, inputs: Map<String, String>) {
        if (_executionState.value is ExecutionUiState.Running) return
        _selectedPipeline.value = pipeline
        _logRows.value = emptyList()
        _executionState.value = ExecutionUiState.Running

        runningJob = serviceScope.launch {
            runUseCase.invoke(pipeline.id, inputs)
                .catch { e ->
                    _executionState.value = ExecutionUiState.Failed(e.message ?: "Unknown error")
                }
                .collect { event -> handleEvent(event) }
        }
    }

    /** Called from gutter action — loads by file path, selects in list, runs with defaults. */
    fun runFromFile(filePath: String) {
        serviceScope.launch {
            // Try to find already-loaded pipeline first
            var pipeline = _pipelines.value.firstOrNull { it.filePath?.toString() == filePath }

            // Not loaded yet — parse directly from file
            if (pipeline == null) {
                pipeline = runCatching { toUiPipelineFromFile(File(filePath)) }.getOrNull()
            }

            pipeline ?: return@launch

            // Ensure it's in the list
            if (_pipelines.value.none { it.id == pipeline.id }) {
                _pipelines.value = _pipelines.value + pipeline
            }

            run(pipeline, pipeline.inputSpecs
                .filter { it.defaultValue.isNotEmpty() }
                .associate { it.key to it.defaultValue })
        }
    }

    fun cancelRun() {
        runningJob?.cancel()
        _executionState.value = ExecutionUiState.Idle
    }

    fun clearLog() {
        _logRows.value = emptyList()
        _executionState.value = ExecutionUiState.Idle
    }

    private fun handleEvent(event: ExecutionEvent) {
        when (event) {
            is ExecutionEvent.GateStarted ->
                _logRows.value += GateRow(event.gateLabel, GateStatus.RUNNING)

            is ExecutionEvent.GateCompleted -> {
                val entry = event.entry
                val status = when (entry.status) {
                    TraceStatus.SUCCESS -> GateStatus.SUCCESS
                    TraceStatus.FAILURE -> GateStatus.FAILURE
                    else -> GateStatus.SUCCESS
                }
                _logRows.value = _logRows.value.map { row ->
                    if (row.gateName == entry.gateLabel && row.status == GateStatus.RUNNING)
                        row.copy(status = status, durationMs = entry.durationMs, message = entry.message)
                    else row
                }
            }

            is ExecutionEvent.PipelineCompleted -> {
                val outputs = event.context.snapshot()
                _executionState.value = ExecutionUiState.Completed(outputs)
                outputs.forEach { (k, v) ->
                    _logRows.value += GateRow(
                        gateName = k,
                        status = GateStatus.OUTPUT,
                        outputLabel = "$k = $v",
                    )
                }
            }

            is ExecutionEvent.PipelineFailed ->
                _executionState.value = ExecutionUiState.Failed(event.error.message ?: "Unknown error")
        }
    }

    private suspend fun loadAllWithPaths(): List<UiPipeline> = withContext(Dispatchers.IO) {
        val dir = project.basePath?.let { File(it, ".flai") } ?: return@withContext emptyList()
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles { f -> f.name.endsWith(".flai.yaml") || f.name.endsWith(".yaml") }
            ?.mapNotNull { file -> runCatching { toUiPipelineFromFile(file) }.getOrNull() }
            ?: emptyList()
    }

    private fun toUiPipelineFromFile(file: File): UiPipeline {
        val pipeline = parser.parse(file.readText())
        val inputSpecs = (pipeline.gates[pipeline.entryGateId] as? InputGate)
            ?.inputSchema
            ?.map { field ->
                InputFieldSpec(
                    key = field.name,
                    label = field.name,
                    defaultValue = field.default ?: "",
                    required = field.required,
                )
            } ?: emptyList()

        return UiPipeline(
            id = pipeline.id,
            name = pipeline.name,
            description = pipeline.description,
            gateCount = pipeline.gates.size,
            filePath = file.toPath(),
            inputSpecs = inputSpecs,
        )
    }

    fun dispose() {
        serviceScope.cancel()
    }
}
