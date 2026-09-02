package me.drew.flai.ui.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.drew.flai.domain.model.InputGate
import me.drew.flai.domain.model.PipelineId
import me.drew.flai.domain.model.TraceStatus
import me.drew.flai.domain.service.ExecutionEvent
import me.drew.flai.domain.service.PipelineValidator
import me.drew.flai.infrastructure.credential.PasswordSafeCredentialResolver
import me.drew.flai.infrastructure.executor.*
import me.drew.flai.infrastructure.layout.FileLayoutStore
import me.drew.flai.infrastructure.layout.LayoutSidecarLocator
import me.drew.flai.infrastructure.llm.HttpLlmClient
import me.drew.flai.infrastructure.pipeline.PipelineFileNames
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import me.drew.flai.infrastructure.pipeline.YamlPipelineRepository
import me.drew.flai.infrastructure.pipeline.YamlPipelineSerializer
import me.drew.flai.infrastructure.preferences.VisualEditorPreferences
import me.drew.flai.infrastructure.template.SimpleTemplateRenderer
import me.drew.flai.infrastructure.tool.DefaultToolRegistry
import me.drew.flai.ui.model.*
import me.drew.flai.usecase.RunPipelineUseCase
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<FlaiPipelineUiService>()
private const val AUTO_HIDE_MILLIS = 4000L

private val WATCH_DEBOUNCE = 300.milliseconds

/**
 * Builds the list row for a pipeline file that failed to parse: the file name without its
 * pipeline extension is shown as the name, the pipeline details stay empty and [errorMessage]
 * is carried in [UiPipeline.parseError].
 */
internal fun toParseErrorEntry(fileName: String, filePath: Path?, errorMessage: String?): UiPipeline =
    UiPipeline(
        id = PipelineId(PipelineFileNames.defaultPipelineIdFor(fileName)),
        name = PipelineFileNames.stripExtension(fileName),
        description = "",
        gateCount = 0,
        filePath = filePath,
        inputSpecs = emptyList(),
        parseError = errorMessage ?: "Unknown parse error",
    )

/**
 * Picks the row that should stay selected after a reload: the same pipeline id when it is still
 * present, otherwise the row for the same file path, otherwise `null` (selection cleared).
 */
internal fun reconcileSelection(pipelines: List<UiPipeline>, previous: UiPipeline?): UiPipeline? {
    if (previous == null) {
        return null
    }
    return pipelines.firstOrNull { it.id == previous.id }
        ?: pipelines.firstOrNull { it.filePath != null && it.filePath == previous.filePath }
}

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

/**
 * Maps a [TraceStatus] to its [GateStatus]. A [TraceStatus.TOLERATED_FAILURE] must map to
 * [GateStatus.TOLERATED_FAILURE] and never to [GateStatus.SUCCESS] (FR-9 / AC-7).
 */
internal fun traceStatusToGateStatus(status: TraceStatus): GateStatus = when (status) {
    TraceStatus.SUCCESS -> GateStatus.SUCCESS
    TraceStatus.FAILURE -> GateStatus.FAILURE
    TraceStatus.TOLERATED_FAILURE -> GateStatus.TOLERATED_FAILURE
    TraceStatus.STARTED -> GateStatus.RUNNING
    TraceStatus.SKIPPED -> GateStatus.SUCCESS
}

@Service(Service.Level.PROJECT)
class FlaiPipelineUiService(private val project: Project) : Disposable {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val toolRegistry = DefaultToolRegistry()

    private val llmClient = HttpLlmClient(PasswordSafeCredentialResolver())
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
            DefaultLlmGateExecutor(llmClient, toolRegistry, renderer, skillLoader),
            DefaultLogicGateExecutor(),
            DefaultToolGateExecutor(toolRegistry),
            DefaultBashGateExecutor(projectBasePath, renderer),
            DefaultReadFileGateExecutor(projectBasePath, renderer),
            DefaultWriteFileGateExecutor(projectBasePath, renderer),
        )
    )

    private val runUseCase = RunPipelineUseCase(repository, pipelineExecutor, validator)

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

    /** Serializes [reloadPipelines] so the list and the selection can never come from different loads. */
    private val reloadMutex = Mutex()

    init {
        startWatchingPipelineFiles()
    }

    /**
     * Reloads the list silently whenever the pipeline files change on disk. Bursts of virtual
     * file-system events collapse into one reload via [WATCH_DEBOUNCE]; a failed reload is logged
     * and the collector keeps running.
     */
    @OptIn(FlowPreview::class)
    private fun startWatchingPipelineFiles() {
        serviceScope.launch {
            repository.watchChanges()
                .debounce(WATCH_DEBOUNCE)
                .collect {
                    try {
                        reloadPipelines(notify = false)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        LOG.warn("Flai: automatic pipeline reload failed", exception)
                    }
                }
        }
    }

    fun saveInputValues(pipelineId: PipelineId, values: Map<String, String>) {
        savedInputs[pipelineId] = values.toMap()
    }

    fun getSavedInputValues(pipelineId: PipelineId): Map<String, String> =
        savedInputs[pipelineId] ?: emptyMap()

    private suspend fun saveAllDocumentsOnEdt() {
        suspendCancellableCoroutine { continuation ->
            ApplicationManager.getApplication().invokeLater(
                {
                    try {
                        FileDocumentManager.getInstance().saveAllDocuments()
                        continuation.resume(Unit)
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    }
                },
                ModalityState.nonModal()
            )
        }
    }

    /** Manual refresh: saves open documents, refreshes the VFS and reports the result. */
    fun refresh() {
        serviceScope.launch {
            try {
                saveAllDocumentsOnEdt()
                repository.refreshVfs()
                reloadPipelines(notify = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Pipeline refresh failed", e)
            }
        }
    }

    /**
     * Reloads every pipeline file into [pipelines] and reconciles the selection with the fresh
     * rows. Shows the "reloaded" balloon only when [notify] is true, so automatic reloads stay silent.
     *
     * Held under [reloadMutex] because a manual [refresh] and the file watcher launch independently
     * on [serviceScope]. Without it two reloads can interleave and publish a list from one load while
     * reconciling the selection against the other, leaving the detail panel on a row the list no
     * longer contains.
     */
    private suspend fun reloadPipelines(notify: Boolean) {
        reloadMutex.withLock {
            val uiPipelines = loadAllWithPaths()
            _pipelines.value = uiPipelines
            _selectedPipeline.update { previous -> reconcileSelection(uiPipelines, previous) }
            if (notify) {
                notifyReloaded(uiPipelines.size)
            }
        }
    }

    private fun notifyReloaded(count: Int) {
        val message = when (count) {
            1 -> "Reloaded 1 pipeline"
            else -> "Reloaded $count pipelines"
        }
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Flai Pipelines")
            .createNotification(message, NotificationType.INFORMATION)
        notification.isImportant = false
        notification.notify(project)
        serviceScope.launch {
            delay(AUTO_HIDE_MILLIS.milliseconds)
            notification.expire()
        }
    }

    fun selectPipeline(pipeline: UiPipeline) {
        _selectedPipeline.value = pipeline
    }

    /**
     * Creates the model ↔ document sync coordinator for one visual editor of [file].
     * The coordinator is disposed together with [parent].
     */
    fun createDocumentSync(file: VirtualFile, parent: Disposable): VisualPipelineDocumentSync {
        val sync = VisualPipelineDocumentSync(
            project = project,
            file = file,
            document = FileDocumentManager.getInstance().getDocument(file),
            parser = parser,
            serializer = YamlPipelineSerializer(),
            validator = validator,
            layoutStore = FileLayoutStore(LayoutSidecarLocator.sidecarFor(project, file)),
            preferences = VisualEditorPreferences(project),
        )
        Disposer.register(parent, sync)
        return sync
    }

    fun run(pipeline: UiPipeline, inputs: Map<String, String>) {
        if (pipeline.parseError != null) {
            LOG.warn("Flai: refusing to run '${pipeline.name}' — the file does not parse")
            return
        }
        if (!claimRunning()) {
            return
        }
        _selectedPipeline.value = pipeline
        _logRows.value = emptyList()

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

    /** Atomically moves the execution state to Running; false when a run is already in progress. */
    private fun claimRunning(): Boolean {
        while (true) {
            val current = _executionState.value
            if (current is ExecutionUiState.Running) {
                return false
            }
            if (_executionState.compareAndSet(current, ExecutionUiState.Running)) {
                return true
            }
        }
    }

    /** Called from gutter action — loads by file path, selects in list, runs with defaults. */
    fun runFromFile(filePath: String) {
        serviceScope.launch {
            // Try to find already-loaded pipeline first
            val loaded = _pipelines.value.firstOrNull { it.filePath?.toString() == filePath }

            // Not loaded yet — parse directly from file
            val pipeline = loaded
                ?: runCatching { toUiPipelineFromFile(File(filePath)) }.getOrNull()
                ?: return@launch

            if (pipeline.parseError != null) {
                LOG.warn("Flai: refusing to run '$filePath' — the file does not parse")
                return@launch
            }

            // Ensure it's in the list, matching on the file it came from
            _pipelines.update { current ->
                if (current.any { it.filePath == pipeline.filePath }) {
                    current
                } else {
                    current + pipeline
                }
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
                _logRows.value += GateRow(event.gateLabel, GateStatus.RUNNING, gateId = event.gateId)

            is ExecutionEvent.GateCompleted -> {
                val entry = event.entry
                val status = traceStatusToGateStatus(entry.status)
                _logRows.value = _logRows.value.map { row ->
                    if (row.gateId == entry.gateId.value && row.status == GateStatus.RUNNING)
                        row.copy(status = status, durationMs = entry.durationMs, message = entry.message)
                    else row
                }
            }

            is ExecutionEvent.ToolCompleted -> {
                _logRows.value += GateRow(
                    gateName = "Tool ${event.report.toolName}",
                    gateId = event.gateId,
                    status = if (event.report.succeeded) GateStatus.SUCCESS else GateStatus.FAILURE,
                    durationMs = event.report.durationMs,
                    message = "round ${event.report.round}",
                    isNested = true,
                )
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
        val files = dir.listFiles { file -> file.isFile && PipelineFileNames.isPipelineFileName(file.name) }
            ?: return@withContext emptyList()
        LOG.info("Flai: found ${files.size} pipeline file(s) in ${dir.absolutePath}")
        files.sortedBy { file -> file.name.lowercase() }.map { file ->
            runCatching { toUiPipelineFromFile(file) }.getOrElse { e ->
                LOG.warn("Flai: failed to load pipeline from ${file.name}: ${e.message}", e)
                toParseErrorEntry(file.name, file.toPath(), e.message)
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
