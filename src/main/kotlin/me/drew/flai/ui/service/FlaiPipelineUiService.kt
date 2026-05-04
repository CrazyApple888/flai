package me.drew.flai.ui.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
        )
    )

    private val runUseCase = RunPipelineUseCase(repository, pipelineExecutor)
    private val listUseCase = ListPipelinesUseCase(repository)

    private val _pipelines = MutableStateFlow<List<UiPipeline>>(emptyList())
    val pipelines: StateFlow<List<UiPipeline>> = _pipelines.asStateFlow()

    private val _executionState = MutableStateFlow<ExecutionUiState>(ExecutionUiState.Idle)
    val executionState: StateFlow<ExecutionUiState> = _executionState.asStateFlow()

    private val _logRows = MutableStateFlow<List<GateRow>>(emptyList())
    val logRows: StateFlow<List<GateRow>> = _logRows.asStateFlow()

    private var runningJob: Job? = null

    fun refresh() {
        serviceScope.launch {
            try {
                val ids = listUseCase.invoke()
                val uiPipelines = ids.mapNotNull { id ->
                    runCatching { toUiPipeline(id) }.getOrNull()
                }
                _pipelines.value = uiPipelines
            } catch (_: Exception) {
            }
        }
    }

    fun run(pipeline: UiPipeline, inputs: Map<String, String>) {
        if (_executionState.value is ExecutionUiState.Running) return
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

    private suspend fun toUiPipeline(id: PipelineId): UiPipeline {
        val pipeline = repository.load(id)
        val inputSpecs = pipeline.gates[pipeline.entryGateId]
            ?.let { gate ->
                if (gate is me.drew.flai.domain.model.InputGate) {
                    gate.inputSchema.map { field ->
                        InputFieldSpec(
                            key = field.name,
                            label = field.name,
                            defaultValue = field.default ?: "",
                            required = field.required,
                        )
                    }
                } else emptyList()
            } ?: emptyList()

        return UiPipeline(
            id = pipeline.id,
            name = pipeline.name,
            description = pipeline.description,
            gateCount = pipeline.gates.size,
            filePath = null,
            inputSpecs = inputSpecs,
        )
    }

    fun dispose() {
        serviceScope.cancel()
    }
}
