package me.drew.flai.ui.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
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
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<FlaiPipelineUiService>()

/**
 * Merges [retained] values over spec defaults. Only keys present in [specs] are included;
 * stale keys from [retained] that are absent from [specs] are implicitly discarded (FR-9).
 */
internal fun mergeInputs(
    specs: List<InputFieldSpec>,
    retained: Map<String, String>,
): Map<String, String> = specs.associate { spec ->
    spec.key to (retained[spec.key] ?: spec.defaultValue)
}

@Service(Service.Level.PROJECT)
class FlaiPipelineUiService(private val project: Project) : Disposable {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val toolRegistry = IdeToolRegistry()

    private val llmClient = HttpLlmClient()
    private val renderer = SimpleTemplateRenderer()
    private val parser = YamlPipelineParser()
    private val validator = PipelineValidator()
    val repository = YamlPipelineRepository(project, parser)

    private val projectBasePath: String = project.basePath
        ?: throw IllegalStateException("Project '${project.name}' has no base path")

    private val skillLoader = SkillLoader(projectBasePath)

    private val pipelineExecutor = CoroutinePipelineExecutor(
        listOf(
            DefaultInputGateExecutor(),
            DefaultOutputGateExecutor(),
            DefaultLlmGateExecutor(llmClient, renderer, skillLoader),
            DefaultLogicGateExecutor(),
            DefaultToolGateExecutor(toolRegistry),
            DefaultBashGateExecutor(projectBasePath, renderer),
            DefaultReadFileGateExecutor(projectBasePath, renderer),
            DefaultWriteFileGateExecutor(projectBasePath, renderer),
        )
    )

    private val runUseCase = RunPipelineUseCase(repository, pipelineExecutor, validator)
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

    private val savedInputs: ConcurrentHashMap<PipelineId, Map<String, String>> = ConcurrentHashMap()

    fun saveInputValues(pipelineId: PipelineId, values: Map<String, String>) {
        savedInputs[pipelineId] = values.toMap()
    }

    fun getSavedInputValues(pipelineId: PipelineId): Map<String, String> =
        savedInputs[pipelineId] ?: emptyMap()

    fun refresh() {
        serviceScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    WriteIntentReadAction.run {
                        FileDocumentManager.getInstance().saveAllDocuments()
                    }
                }
                repository.refreshVfs()
                val uiPipelines = loadAllWithPaths()
                _pipelines.value = uiPipelines
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Pipeline refresh failed", e)
            }
        }
    }

    fun selectPipeline(pipeline: UiPipeline) {
        _selectedPipeline.value = pipeline
    }

    fun run(pipeline: UiPipeline, inputs: Map<String, String>) {
        if (_executionState.value is ExecutionUiState.Running) {
            return
        }
        _selectedPipeline.value = pipeline
        _logRows.value = emptyList()
        _executionState.value = ExecutionUiState.Running

        runningJob = serviceScope.launch {
            runUseCase.invoke(pipeline.id, inputs)
                .catch { e ->
                    val message = e.message ?: "Unknown error"
                    _logRows.value += GateRow("Error", GateStatus.FAILURE, message = message)
                    _executionState.value = ExecutionUiState.Failed(message)
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

            val inputs = mergeInputs(pipeline.inputSpecs, getSavedInputValues(pipeline.id))
            run(pipeline, inputs)
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
                val outputs = event.outputs
                _executionState.value = ExecutionUiState.Completed(outputs)
                outputs.forEach { (k, v) ->
                    _logRows.value += GateRow(
                        gateName = k,
                        status = GateStatus.OUTPUT,
                        outputLabel = "$k = $v",
                        outputValue = v?.toString(),
                    )
                }
            }

            is ExecutionEvent.PipelineFailed -> {
                val message = event.error.message ?: "Unknown error"
                if (_logRows.value.none { it.status == GateStatus.FAILURE }) {
                    _logRows.value += GateRow("Pipeline failed", GateStatus.FAILURE, message = message)
                }
                _executionState.value = ExecutionUiState.Failed(message)
            }
        }
    }

    private suspend fun loadAllWithPaths(): List<UiPipeline> = withContext(Dispatchers.IO) {
        val dir = project.basePath?.let { File(it, ".flai") } ?: return@withContext emptyList()
        if (!dir.exists()) {
            return@withContext emptyList()
        }
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".flai.yaml") || f.name.endsWith(".yaml")) }
            ?: return@withContext emptyList()
        LOG.info("Flai: found ${files.size} pipeline file(s) in ${dir.absolutePath}")
        files.mapNotNull { file ->
            runCatching { toUiPipelineFromFile(file) }.getOrElse { e ->
                LOG.warn("Flai: failed to load pipeline from ${file.name}: ${e.message}", e)
                null
            }
        }
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

    override fun dispose() {
        serviceScope.cancel()
    }
}
