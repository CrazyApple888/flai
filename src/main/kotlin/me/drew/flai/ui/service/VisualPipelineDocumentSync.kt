package me.drew.flai.ui.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.drew.flai.domain.service.PipelineValidator
import me.drew.flai.infrastructure.pipeline.PipelineFileNames
import me.drew.flai.infrastructure.pipeline.YamlPipelineParser
import me.drew.flai.infrastructure.pipeline.YamlPipelineSerializer
import me.drew.flai.infrastructure.preferences.VisualEditorPreferences
import me.drew.flai.ui.util.coroutineScope
import me.drew.flai.ui.visual.GatePosition
import me.drew.flai.ui.visual.LayoutStore
import me.drew.flai.ui.visual.ValidationError
import me.drew.flai.ui.visual.ValidationResult
import me.drew.flai.ui.visual.VisualPipelineModel
import me.drew.flai.ui.visual.VisualPipelineValidator

private const val AUTO_SYNC_DEBOUNCE_MS = 300L
private const val RELOAD_DEBOUNCE_MS = 300L

/**
 * Keeps one [VisualPipelineModel] and its YAML [Document] in sync, in both directions:
 * model edits are debounced and written back; external document edits are debounced and
 * reloaded into the model. All work runs on the EDT. One instance per open visual editor.
 */
class VisualPipelineDocumentSync(
    private val project: Project,
    private val file: VirtualFile,
    private val document: Document?,
    private val parser: YamlPipelineParser,
    private val serializer: YamlPipelineSerializer,
    private val validator: PipelineValidator,
    private val layoutStore: LayoutStore,
    private val preferences: VisualEditorPreferences,
) : Disposable {

    sealed class SyncState {
        object Idle : SyncState()

        /** [writeCount] makes consecutive saves distinct so state observers see every write. */
        data class Saved(val writeCount: Int) : SyncState()
        data class Blocked(val reason: SyncBlockReason, val message: String) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    sealed class ApplyOutcome {
        object Written : ApplyOutcome()

        /** The YAML was written; gate-field rules still fail, so the pipeline cannot run yet. */
        data class WrittenWithValidationErrors(val errors: List<ValidationError>) : ApplyOutcome()

        /** Structure is broken (gate ids, entry gate) — nothing was written. */
        data class StructureInvalid(val errors: List<ValidationError>) : ApplyOutcome()
        object NeedsNormalizeConfirmation : ApplyOutcome()
        data class WriteFailed(val message: String) : ApplyOutcome()
    }

    private sealed class LoadResult {
        data class Loaded(val model: VisualPipelineModel) : LoadResult()
        data class ParseFailed(val message: String) : LoadResult()
    }

    private val scope = coroutineScope()

    val model: VisualPipelineModel

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _documentReloaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val documentReloaded: SharedFlow<Unit> = _documentReloaded.asSharedFlow()

    /** True when the document text parses (or is blank). Auto-sync never overwrites unparsable YAML. */
    private var documentParsable = true

    /** True when serialising the model would change the document's formatting or drop comments. */
    private var normalizationRequired = false

    /** Text this coordinator wrote last; a document change back to it is not an external edit. */
    private var lastWrittenText: String? = null
    private var lastSavedPositions: Map<String, GatePosition> = emptyMap()
    private var writeCount = 0

    /** Set while this coordinator itself mutates and saves the document. */
    private var applyInProgress = false
    private var autoSyncJob: Job? = null
    private var reloadJob: Job? = null

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (applyInProgress) {
                return
            }
            scheduleReload()
        }
    }

    init {
        model = when (val loaded = loadModel(document?.text ?: "")) {
            is LoadResult.Loaded -> loaded.model
            is LoadResult.ParseFailed -> {
                _syncState.value = SyncState.Blocked(
                    SyncBlockReason.DOCUMENT_UNPARSABLE,
                    "Parse error: ${loaded.message}",
                )
                seedDefaults(VisualPipelineModel())
            }
        }
        model.onChanged = {
            _dirty.value = model.isDirty
            scheduleAutoSync()
        }
        document?.addDocumentListener(documentListener, this)
    }

    fun scheduleAutoSync() {
        autoSyncJob?.cancel()
        val stampAtSchedule = document?.modificationStamp
        autoSyncJob = scope.launch(Dispatchers.EDT) {
            delay(AUTO_SYNC_DEBOUNCE_MS)
            if (reloadJob?.isActive == true) {
                scheduleAutoSync()
                return@launch
            }
            if (document?.modificationStamp != stampAtSchedule) {
                return@launch
            }
            autoSync()
        }
    }

    /**
     * Cancels the pending auto-sync and writes now when the model is dirty.
     * Returns [SyncDecision.Proceed] once the document on disk reflects the model.
     */
    fun flushSync(): SyncDecision {
        autoSyncJob?.cancel()
        if (reloadJob?.isActive == true) {
            reloadJob?.cancel()
            reloadFromDocument()
        }
        if (!model.isDirty) {
            saveDocumentIfUnsaved()
            return SyncDecision.Proceed
        }
        val decision = decideAutoSync()
        when (decision) {
            is SyncDecision.Blocked -> {
                _syncState.value = SyncState.Blocked(decision.reason, decision.message)
                return decision
            }

            SyncDecision.Proceed -> {
                val outcome = writeModelToDocument()
                if (outcome is ApplyOutcome.WriteFailed) {
                    return SyncDecision.Blocked(SyncBlockReason.WRITE_FAILED, outcome.message)
                }
                return SyncDecision.Proceed
            }
        }
    }

    /**
     * Writes first, then validates: only an unparsable document or a broken structure blocks the
     * write. Never shows dialogs; the caller decides how to present the outcome.
     */
    fun apply(): ApplyOutcome {
        autoSyncJob?.cancel()
        if (!documentParsable) {
            return writeFailed(SyncPolicy.UNPARSABLE_MESSAGE)
        }
        val structure = VisualPipelineValidator.validateStructure(model)
        if (!structure.isValid) {
            return ApplyOutcome.StructureInvalid(structure.errors)
        }
        if (normalizationRequired && !preferences.isNormalizeAccepted(file)) {
            return ApplyOutcome.NeedsNormalizeConfirmation
        }
        val outcome = writeModelToDocument()
        if (outcome !is ApplyOutcome.Written) {
            return outcome
        }
        val result = validateFully()
        if (!result.isValid) {
            return ApplyOutcome.WrittenWithValidationErrors(result.errors)
        }
        return outcome
    }

    /** Every rule — structure plus the core gate/edge rules. Used by Apply and Run after the write. */
    fun validateFully(): ValidationResult = VisualPipelineValidator.validate(model, validator)

    fun acceptNormalizeAndApply(): ApplyOutcome {
        preferences.acceptNormalize(file)
        return apply()
    }

    private fun autoSync() {
        if (!model.isDirty) {
            return
        }
        when (val decision = decideAutoSync()) {
            is SyncDecision.Blocked -> _syncState.value = SyncState.Blocked(decision.reason, decision.message)
            SyncDecision.Proceed -> writeModelToDocument()
        }
    }

    private fun decideAutoSync(): SyncDecision {
        val structure = VisualPipelineValidator.validateStructure(model)
        return SyncPolicy.decide(
            SyncPreconditions(
                documentParsable = documentParsable,
                hasNodes = model.nodes.isNotEmpty(),
                structureErrors = structure.errors,
                normalizationRequired = normalizationRequired,
                normalizeAccepted = preferences.isNormalizeAccepted(file),
            ),
        )
    }

    private fun writeModelToDocument(): ApplyOutcome {
        val doc = document
        if (doc == null || !doc.isWritable) {
            return writeFailed("Cannot write YAML: document is read-only")
        }
        val pipeline = model.toPipeline()
        val serialized = serializer.serialize(pipeline)
        try {
            parser.parse(serialized)
        } catch (e: Exception) {
            return writeFailed("Cannot write YAML: ${e.message}")
        }
        if (doc.text != serialized) {
            applyInProgress = true
            try {
                WriteCommandAction.runWriteCommandAction(project, "Apply Visual Pipeline", file.path, Runnable {
                    doc.setText(serialized)
                })
                FileDocumentManager.getInstance().saveDocument(doc)
            } catch (e: Exception) {
                return writeFailed("Cannot write YAML: ${e.message}")
            } finally {
                applyInProgress = false
            }
        }
        lastWrittenText = serialized
        documentParsable = true
        normalizationRequired = false
        saveLayoutIfChanged()
        model.clearDirty()
        _dirty.value = false
        writeCount += 1
        _syncState.value = SyncState.Saved(writeCount)
        return ApplyOutcome.Written
    }

    private fun writeFailed(message: String): ApplyOutcome.WriteFailed {
        _syncState.value = SyncState.Error(message)
        return ApplyOutcome.WriteFailed(message)
    }

    private fun saveLayoutIfChanged() {
        val positions = model.nodes.associate { it.gateId to GatePosition(it.x, it.y) }
        if (positions == lastSavedPositions) {
            return
        }
        layoutStore.save(positions)
        lastSavedPositions = positions
    }

    private fun saveDocumentIfUnsaved() {
        val doc = document ?: return
        val manager = FileDocumentManager.getInstance()
        if (manager.isDocumentUnsaved(doc)) {
            manager.saveDocument(doc)
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = scope.launch(Dispatchers.EDT) {
            delay(RELOAD_DEBOUNCE_MS)
            reloadFromDocument()
        }
    }

    private fun reloadFromDocument() {
        val text = document?.text ?: return
        if (text == lastWrittenText) {
            return
        }
        when (val loaded = loadModel(text)) {
            is LoadResult.ParseFailed -> {
                _syncState.value = SyncState.Blocked(
                    SyncBlockReason.DOCUMENT_UNPARSABLE,
                    "Parse error: ${loaded.message}",
                )
            }

            is LoadResult.Loaded -> {
                model.replaceWith(loaded.model)
                model.clearHistory()
                _dirty.value = model.isDirty
                _syncState.value = SyncState.Idle
                _documentReloaded.tryEmit(Unit)
            }
        }
    }

    /** Parses [text] into a fresh model and refreshes [documentParsable] / [normalizationRequired]. */
    private fun loadModel(text: String): LoadResult {
        if (text.isBlank()) {
            documentParsable = true
            normalizationRequired = false
            return LoadResult.Loaded(seedDefaults(VisualPipelineModel()))
        }
        return try {
            val pipeline = parser.parse(text)
            documentParsable = true
            normalizationRequired = serializer.serialize(pipeline) != text
            val positions = layoutStore.load()
            lastSavedPositions = positions
            LoadResult.Loaded(seedDefaults(VisualPipelineModel.fromPipeline(pipeline, positions)))
        } catch (e: Exception) {
            documentParsable = false
            normalizationRequired = true
            LoadResult.ParseFailed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun seedDefaults(candidate: VisualPipelineModel): VisualPipelineModel {
        if (candidate.pipelineId.isBlank()) {
            val defaultId = PipelineFileNames.defaultPipelineIdFor(file.name)
            candidate.setPipelineMetadata(defaultId, defaultId)
            candidate.clearDirty()
        }
        return candidate
    }

    override fun dispose() {
        autoSyncJob?.cancel()
        reloadJob?.cancel()
    }
}
