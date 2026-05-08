# Architecture: Visual Node-and-Edge Pipeline Editor

## Overview

This feature adds a **Visual** tab to the IntelliJ file editor for every `*.flai.yaml` file. The tab is a self-contained `FileEditor` assembled from five Swing panels — canvas, property panel, gate palette, toolbar, and error banner — backed by an in-editor state model (`VisualPipelineModel`) that is independent of `FlaiPipelineUiService`. The service is used read-only for execution-overlay data (`logRows`, `executionState`).

Layers touched:
- **`infrastructure/pipeline/`** — new `YamlPipelineSerializer` (write path, mirrors `YamlPipelineParser`)
- **`ui/editor/`** — new `FlaiPipelineFileEditor`, `FlaiPipelineFileEditorProvider`, and supporting panels
- **`ui/visual/`** — new package for all visual-editor files: model, canvas, layout, palette, property panel, ports extension
- **`plugin.xml`** — one new `fileEditorProvider` extension point

Domain model (`Gate.kt`, `Pipeline.kt`, `PipelineEdge`) is **read-only** — no changes.

---

## Tech Decisions

**Decision:** `FileEditorProvider` + `FileEditor` with `FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR`
**Reason:** Produces the standard two-tab layout ("Text" then "Visual") using IntelliJ's built-in tab mechanism. No custom tab-bar Swing code needed. Pre-decided; not re-opened.

**Decision:** Pure `Graphics2D` on `JPanel` — no third-party graph library
**Reason:** Zero new Gradle dependencies. Platform is IntelliJ 2025.2.6.1; adding a graph library would require dependency review and runtime shading. Pre-decided.

**Decision:** `YamlPipelineSerializer` hand-rolls block-style YAML using `StringBuilder`
**Reason:** SnakeYAML's dump output does not match the spec's canonical key ordering and collapses multi-line strings. The serializer mirrors the key ordering that `YamlPipelineParser` expects so round-trip is stable. No new dependency. Pre-decided.

**Decision:** `LayoutStore` reads the sidecar file using SnakeYAML (`Yaml().load<Map<String,Any>>(content)`) and writes it by hand-rolling a flat JSON string with `StringBuilder`
**Reason:** JSON is a strict subset of YAML, so SnakeYAML parses `.layout.json` without any additional library. Writing is a trivially simple flat object (`{ "gateId": { "x": int, "y": int } }`) that a `StringBuilder` can produce in a handful of lines. No new Gradle dependency is introduced. SnakeYAML is already on the classpath via the bundled `org.jetbrains.plugins.yaml` plugin.

**Decision:** Run-from-Visual executes the last-written-to-disk buffer via `FileDocumentManager.getInstance().saveDocument(document)` before delegating to `service.runFromFile(filePath)`
**Reason:** `FlaiPipelineUiService.runFromFile` reads from disk (`File(filePath).readText()`). After Apply the document is modified but unsaved. Calling `saveDocument` first synchronises disk with the buffer, ensuring the visual Run always executes the Applied state. The alternative (adding `runFromContent` to the service) would change a shared service used by the tool window, which is out of scope.

**Decision:** Per-file "normalization-warning accepted" stored in `PropertiesComponent.getInstance(project)` keyed by `"flai.applyWarningAccepted." + virtualFile.url`
**Reason:** Project-level `PropertiesComponent` persists across IDE restarts and is scoped to the project, satisfying NFR-3. No new storage APIs needed.

**Decision:** `VisualPipelineModel` assigns a stable `Int` node key (`nodeSeq`) to each gate on creation; the key is separate from `GateId` and never changes on ID rename
**Reason:** Node selection, drag state, and port-handle hit-testing all hold references by `nodeSeq`. If selection tracked `GateId` directly, an ID rename (FR-20) during an active selection would silently break the reference. The `nodeSeq` → `GateId` map is the single mutable mapping.

**Decision:** Execution-overlay matching uses `GateRow.gateName` (which holds the gate `label`, not the gate `id`)
**Reason:** `FlaiPipelineUiService` emits `GateRow(event.gateLabel, ...)` sourced from `gate.label`. Nodes must look up their overlay status by `gate.label`. If two gates share a label, both nodes will show the same badge — this is a cosmetic limitation, not a data-correctness issue. Documented as a known limitation.

**Decision:** `DocumentListener` sets a dirty flag (`externalEditFlag`) but does not re-parse on every keystroke; re-parse occurs in `FileEditor.selectNotify()`
**Reason:** Parsing YAML on every keystroke in the text editor would be slow and create excessive churn. The flag-then-parse-on-activate pattern is used by IntelliJ's own split-editors (e.g., Markdown preview).

**Decision:** `FlaiPipelineFileEditor` uses `coroutineScope()` from `CoroutineExt.kt` (auto-cancelled via `Disposer`) and registers its `DocumentListener` with the editor as the `Disposable` parent: `document.addDocumentListener(listener, this)`
**Reason:** Matches the project-wide convention mandated in CLAUDE.md. Auto-detaches the listener on editor disposal without a manual `removeDocumentListener` call. Prevents coroutine leaks on tab close.

**Decision:** New `VisualPipelineValidator` operating directly on `VisualPipelineModel` (not on a `Pipeline` domain object)
**Reason:** The model can hold partial/empty-string fields (e.g., a newly dropped `LlmGate` with blank `promptTemplate`) that `YamlPipelineParser` would reject. Validation must run before serialisation so it must operate on the model. The domain `PipelineValidator` is not reused here.

---

## Data Model

### VisualNode (in-memory only, not persisted)

```kotlin
data class VisualNode(
    val nodeSeq: Int,                   // stable internal key; never changes
    var gateId: String,                 // mutable: ID rename cascades here
    var gate: Gate,                     // full domain Gate; replaced on property-panel edits
    var x: Int,                         // canvas position in logical pixels
    var y: Int,
)
```

### VisualPipelineModel (owns all mutable visual state)

```kotlin
class VisualPipelineModel {
    // Pipeline-level metadata
    var pipelineId: String
    var pipelineName: String
    var pipelineDescription: String
    var entryNodeSeq: Int               // nodeSeq of entry gate

    // Nodes and edges
    val nodes: MutableList<VisualNode>
    val edges: MutableList<VisualEdge>

    // Dirty tracking
    var isDirty: Boolean = false        // set true on any mutation; cleared on Apply

    // Sequence counter
    private var nextSeq: Int = 0
    fun nextNodeSeq(): Int = nextSeq++

    // Lookups
    fun nodeBySeq(seq: Int): VisualNode?
    fun nodeByGateId(id: String): VisualNode?

    // Mutations (all set isDirty = true)
    fun addNode(gate: Gate, x: Int, y: Int): VisualNode
    fun removeNode(seq: Int)            // also removes connected edges
    fun renameGateId(seq: Int, newId: String): Boolean  // false if collision/empty
    fun addEdge(edge: VisualEdge): Boolean  // false if duplicate
    fun removeEdge(edge: VisualEdge)
    fun moveNode(seq: Int, x: Int, y: Int)
    fun updateGate(seq: Int, gate: Gate)

    // Snapshot for serialiser / validator
    fun toPipeline(): Pipeline
}
```

### VisualEdge

```kotlin
data class VisualEdge(
    val fromSeq: Int,                   // nodeSeq of source node
    val fromPort: String = "out",
    val toSeq: Int,                     // nodeSeq of target node
    val toPort: String = "in",
)
```

Note: `VisualEdge` stores `nodeSeq` references internally; `toPipeline()` resolves them to `GateId` strings when building the `PipelineEdge` list.

### LayoutStore sidecar JSON schema

```json
{
  "gates": {
    "<gateId>": { "x": 120, "y": 240 }
  }
}
```

### Palette Gate Defaults (FR-8)

When a gate type is dragged from the palette onto the canvas, `VisualPipelineModel.addNode()` creates a new `Gate` with the following defaults. Required fields that have no sensible default are initialised to empty string `""`. The gate will fail `VisualPipelineValidator` until the user fills them in.

| Gate type | Generated ID pattern | Gate constructor defaults |
|---|---|---|
| `InputGate` | `input-<seq>` | `label = id.value`, `inputSchema = emptyList()` |
| `OutputGate` | `output-<seq>` | `label = id.value`, `outputMapping = emptyMap()` |
| `LlmGate` | `llm-<seq>` | `label = id.value`, `promptTemplate = ""`, `skills = emptyList()`, `inputMapping = emptyMap()`, `outputMapping = mapOf("response" to "response")`, `endpointConfig = LlmEndpointConfig(url = "", credentialId = "", model = "", params = emptyMap())` |
| `LogicGate` | `logic-<seq>` | `label = id.value`, `branches = emptyList()`, `defaultPort = "default"` |
| `ToolGate` | `tool-<seq>` | `label = id.value`, `toolName = ""`, `inputMapping = emptyMap()`, `outputMapping = emptyMap()` |
| `ReadFileGate` | `read-file-<seq>` | `label = id.value`, `path = ""`, `outputKey = "content"` |
| `WriteFileGate` | `write-file-<seq>` | `label = id.value`, `path = ""`, `contentKey = ""`, `mode = WriteMode.OVERWRITE` |

`<seq>` is the next available integer suffix that produces a unique gate ID within the current pipeline (e.g., `llm-1`, `llm-2`). The uniqueness check is performed against existing `gateId` strings in `VisualPipelineModel`.

### ValidationResult

```kotlin
data class ValidationError(val gateId: String, val field: String, val message: String)
data class ValidationResult(val errors: List<ValidationError>) {
    val isValid: Boolean get() = errors.isEmpty()
}
```

---

## APIs / Interfaces

### GatePorts.kt — extension functions

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/GatePorts.kt
package me.drew.flai.ui.visual

import me.drew.flai.domain.model.*

fun Gate.inputPorts(): List<String> = when (this) {
    is InputGate -> emptyList()
    else -> listOf("in")
}

fun Gate.outputPorts(): List<String> = when (this) {
    is InputGate -> listOf("out")
    is LogicGate -> branches.map { it.port } + listOfNotNull(defaultPort)
    else -> listOf("out")
}
```

### YamlPipelineSerializer

```kotlin
// src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializer.kt
package me.drew.flai.infrastructure.pipeline

import me.drew.flai.domain.model.Pipeline

class YamlPipelineSerializer {
    /**
     * Serialises a Pipeline to canonical block-style YAML.
     * Key ordering matches YamlPipelineParser expectations:
     *   top-level: id, name, description, entry, gates, edges
     *   gate: type, label, then type-specific fields
     */
    fun serialize(pipeline: Pipeline): String
}
```

### LayoutStore

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/LayoutStore.kt
package me.drew.flai.ui.visual

import java.io.File

data class GatePosition(val x: Int, val y: Int)

class LayoutStore(private val sidecarFile: File) {
    /** Returns stored positions; missing gates get null (caller uses auto-layout). */
    fun load(): Map<String, GatePosition>

    /** Overwrites the sidecar file with current positions. */
    fun save(positions: Map<String, GatePosition>)
}
```

### PipelineAutoLayout

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/PipelineAutoLayout.kt
package me.drew.flai.ui.visual

import me.drew.flai.domain.model.Pipeline

data class AutoLayoutResult(val positions: Map<String, Pair<Int, Int>>)

object PipelineAutoLayout {
    /**
     * BFS topological rank from entryGateId.
     * Column = rank; row = position within rank.
     * Grid spacing: NODE_W + H_GAP horizontally, NODE_H + V_GAP vertically.
     */
    fun compute(pipeline: Pipeline): AutoLayoutResult

    const val NODE_W = 140
    const val NODE_H = 60
    const val H_GAP = 60
    const val V_GAP = 30
}
```

### VisualPipelineValidator

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/VisualPipelineValidator.kt
package me.drew.flai.ui.visual

object VisualPipelineValidator {
    /**
     * Validates all required fields per FR-27 plus edge integrity (EC-10, EC-11).
     * Returns a ValidationResult; caller blocks Apply if !isValid.
     */
    fun validate(model: VisualPipelineModel): ValidationResult
}
```

Required-field rules enforced by `validate()`:
- Pipeline: `pipelineId` non-empty; `entryNodeSeq` references an existing node; `entry` gate exists in nodes
- `InputGate`: gate ID non-empty
- `OutputGate`: gate ID non-empty
- `LlmGate`: gate ID, `promptTemplate`, `endpointConfig.url`, `endpointConfig.credentialId`, `endpointConfig.model` all non-empty
- `LogicGate`: gate ID, `defaultPort` non-empty; each branch has non-empty `port` and a valid (non-null) condition
- `ToolGate`: gate ID, `toolName` non-empty
- `ReadFileGate`: gate ID, `path`, `outputKey` non-empty
- `WriteFileGate`: gate ID, `path`, `contentKey`, `mode` non-empty
- Edge integrity: every `from`/`to` GateId in edges exists in nodes (EC-10); every `fromPort` exists on its source gate's `outputPorts()` (EC-11)

### PipelineCanvas

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/PipelineCanvas.kt
package me.drew.flai.ui.visual

import javax.swing.JPanel

/**
 * Custom JPanel. paintComponent() draws all nodes, edges, port handles, and
 * execution-state badges using Graphics2D.
 *
 * Interaction callbacks (lambdas set by FlaiPipelineFileEditor):
 */
class PipelineCanvas(private val model: VisualPipelineModel) : JPanel() {
    var onNodeSelected: ((nodeSeq: Int?) -> Unit) = {}
    var onBackgroundClicked: (() -> Unit) = {}
    var onNodeMoved: ((nodeSeq: Int, x: Int, y: Int) -> Unit) = {}
    var onEdgeAdded: ((VisualEdge) -> Unit) = {}
    var onEdgeDeleted: ((VisualEdge) -> Unit) = {}
    var onNodeDeleted: ((nodeSeq: Int) -> Unit) = {}
    var onSetAsEntry: ((nodeSeq: Int) -> Unit) = {}
    var onPaletteDropped: ((gateType: String, x: Int, y: Int) -> Unit) = {}

    /** Called from EDT after model mutations to trigger repaint. */
    fun refresh()

    /** Set execution overlay state; triggers repaint. */
    fun setOverlay(states: Map<String, GateStatus>)  // keyed by gate label

    /** Enable/disable all canvas interactions (locked during execution, EC-9a). */
    var isEditable: Boolean = true
}
```

### NodePropertyPanel

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/NodePropertyPanel.kt
package me.drew.flai.ui.visual

import me.drew.flai.domain.model.Gate
import me.drew.flai.infrastructure.tool.IdeToolRegistry
import javax.swing.JPanel

/**
 * Right-side panel. showGate() rebuilds form contents for the given gate type.
 * showPipelineMeta() shows pipeline-level id/name/description fields.
 * showEmpty() clears the panel when nothing is selected.
 */
class NodePropertyPanel(private val toolRegistry: IdeToolRegistry) : JPanel() {
    var onGateChanged: ((nodeSeq: Int, gate: Gate) -> Unit) = {}
    var onGateIdRenamed: ((nodeSeq: Int, newId: String) -> Boolean) = { _, _ -> false }
    var onBranchPortRenamed: ((nodeSeq: Int, oldPort: String, newPort: String) -> Unit) = {}
    var onBranchDeleted: ((nodeSeq: Int, port: String) -> Unit) = {}
    var onPipelineMetaChanged: ((id: String, name: String, description: String) -> Unit) = {}

    /** Disable all form controls during execution (EC-9a). */
    var isEditable: Boolean = true
        set(value) { field = value; updateEditability() }

    fun showGate(node: VisualNode)
    fun showPipelineMeta(id: String, name: String, description: String)
    fun showEmpty()
    private fun updateEditability()  // iterates all child components and sets enabled
}
```

### GatePalettePanel

```kotlin
// src/main/kotlin/me/drew/flai/ui/visual/GatePalettePanel.kt
package me.drew.flai.ui.visual

import javax.swing.JPanel

/**
 * Vertical list of 7 gate type buttons with TransferHandler.
 * Drag initiates a DnD with transferable payload = gateType string.
 * PipelineCanvas accepts the drop and calls onPaletteDropped.
 */
class GatePalettePanel : JPanel() {
    /** Disable drag-to-canvas during execution (EC-9a). */
    var isEditable: Boolean = true
}
```

### FlaiPipelineFileEditor

```kotlin
// src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditor.kt
package me.drew.flai.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project

class FlaiPipelineFileEditor(
    private val project: Project,
    private val virtualFile: VirtualFile,
) : FileEditor {
    // Implements: getName, getComponent, getPreferredFocusedComponent,
    //             isModified, isValid, selectNotify, deselectNotify, dispose
}
```

### FlaiPipelineFileEditorProvider

```kotlin
// src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditorProvider.kt
package me.drew.flai.ui.editor

import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorPolicy

class FlaiPipelineFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".flai.yaml")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        FlaiPipelineFileEditor(project, file)

    override fun getEditorTypeId(): String = "flai-visual-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
```

---

## File-Level Plan

**New files:**

- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializer.kt` — create: hand-rolled canonical YAML serialiser mirroring `YamlPipelineParser` key ordering; no new deps
- `src/main/kotlin/me/drew/flai/ui/visual/GatePorts.kt` — create: `Gate.inputPorts()` and `Gate.outputPorts()` extension functions (pure domain, no IntelliJ deps)
- `src/main/kotlin/me/drew/flai/ui/visual/LayoutStore.kt` — create: sidecar JSON load/save via SnakeYAML (reads) and hand-rolled flat JSON object (writes)
- `src/main/kotlin/me/drew/flai/ui/visual/VisualNode.kt` — create: `VisualNode` data class and `VisualEdge` data class
- `src/main/kotlin/me/drew/flai/ui/visual/VisualPipelineModel.kt` — create: mutable visual state, toPipeline(), all mutation methods
- `src/main/kotlin/me/drew/flai/ui/visual/VisualPipelineValidator.kt` — create: validate(model) per FR-27 + EC-10 + EC-11
- `src/main/kotlin/me/drew/flai/ui/visual/PipelineAutoLayout.kt` — create: BFS topological rank layout from entryGateId
- `src/main/kotlin/me/drew/flai/ui/visual/PipelineCanvas.kt` — create: Graphics2D JPanel, mouse interaction, DnD drop target, execution overlay
- `src/main/kotlin/me/drew/flai/ui/visual/NodePropertyPanel.kt` — create: per-gate-type Swing forms (7 gate types), pipeline-meta panel
- `src/main/kotlin/me/drew/flai/ui/visual/GatePalettePanel.kt` — create: 7-entry palette with DnD TransferHandler
- `src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditor.kt` — create: assembles all panels, holds `VisualPipelineModel`, DocumentListener, toolbar (Apply/Run/Cancel), tab-switch logic
- `src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditorProvider.kt` — create: `FileEditorProvider` accepting `*.flai.yaml`

**Test files (new):**

- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializerTest.kt` — create: round-trip tests for all 7 gate types
- `src/test/kotlin/me/drew/flai/ui/visual/GatePortsTest.kt` — create: port enumeration for each gate type including LogicGate branches
- `src/test/kotlin/me/drew/flai/ui/visual/VisualPipelineModelTest.kt` — create: rename cascade, edge dedup, removeNode cleans edges
- `src/test/kotlin/me/drew/flai/ui/visual/VisualPipelineValidatorTest.kt` — create: per-gate-type required-field coverage, EC-10/EC-11
- `src/test/kotlin/me/drew/flai/ui/visual/PipelineAutoLayoutTest.kt` — create: BFS rank ordering, no-overlap guarantee for simple chains and forks

**Modified files:**

- `src/main/resources/META-INF/plugin.xml` — modify: add `fileEditorProvider` extension point

---

## Implementation Order

1. **YamlPipelineSerializer + YamlPipelineSerializerTest** — pure Kotlin, no IntelliJ deps; unblocks everything downstream. Test by round-tripping a `Pipeline` through `YamlPipelineParser → Pipeline → YamlPipelineSerializer → YamlPipelineParser → Pipeline` and asserting structural equality.

2. **GatePorts.kt + GatePortsTest** — three lines of code; purely domain logic; unblocks canvas port-handle rendering and edge validation.

3. **LayoutStore.kt** — file I/O only; no IntelliJ APIs; unit-testable with temp files. Write the flat JSON serialiser (hand-rolled `StringBuilder`); read via `Yaml().load<Map<String,Any>>(content)` as SnakeYAML parses JSON.

4. **VisualNode.kt + VisualPipelineModel.kt + VisualPipelineModelTest** — in-memory state; pure Kotlin; no IntelliJ deps. Tests cover: `renameGateId` cascades to edges, duplicate edge prevention, `removeNode` removes connected edges, `toPipeline()` round-trip.

5. **VisualPipelineValidator.kt + VisualPipelineValidatorTest** — depends on `VisualPipelineModel` and `GatePorts`; pure Kotlin. Tests cover: each required-field rule, EC-10 (broken edge), EC-11 (invalid port).

6. **PipelineAutoLayout.kt + PipelineAutoLayoutTest** — depends on `Pipeline` domain model only; pure Kotlin. BFS from `entryGateId`; assign column = BFS rank, row = insertion order within rank.

7. **PipelineCanvas.kt** — Swing/Graphics2D; no test (visual); depends on `VisualPipelineModel`, `GatePorts`, `VisualPipelineValidator` (for Apply pre-check). Implement in this order: static rendering → node drag → edge drag → DnD drop target → context menus → overlay badges.

8. **NodePropertyPanel.kt** — Swing forms; depends on `VisualPipelineModel`; no unit test (Swing). Implement gate types in this order: simple gates (`InputGate`, `OutputGate`, `ReadFileGate`, `WriteFileGate`) → `ToolGate` (dropdown) → `LlmGate` (textarea + params map) → `LogicGate` (branch list with condition-type selector) → pipeline-meta panel.

9. **GatePalettePanel.kt** — Swing DnD; depends on nothing. Implement `TransferHandler` that sets transferable payload to gate-type string; canvas `DropTarget` reads this string.

10. **FlaiPipelineFileEditor.kt** — assembles all panels; depends on all of the above plus IntelliJ APIs (`FileDocumentManager`, `WriteCommandAction`, `PropertiesComponent`, `FlaiPipelineUiService`). Logic implemented in this order:
    - Constructor: parse YAML → build `VisualPipelineModel` → apply layout positions from `LayoutStore` → `PipelineAutoLayout` for missing gates
    - `selectNotify()`: check `externalEditFlag`, optionally show FR-32 prompt, re-parse if needed
    - Apply button handler: `VisualPipelineValidator.validate()` → FR-28 dialog → `YamlPipelineSerializer.serialize()` → `WriteCommandAction` → `LayoutStore.save()`
    - Run button handler: `FileDocumentManager.saveDocument(document)` → `service.runFromFile(virtualFile.path)`
    - Coroutine: collect `service.logRows` + `service.executionState` → derive `Map<String, GateStatus>` keyed by gate label → `canvas.setOverlay()`
    - `DocumentListener` on `document`: set `externalEditFlag = true`
    - EC-9 / EC-9a: when `executionState` transitions to `Running`, disable all editing: set `canvas.isEditable = false`, `propertyPanel.isEditable = false`, `palettePanel.isEditable = false`, and disable the Apply button. Restore all to `true` when `executionState` transitions to any terminal state (`Idle`, `Completed`, `Failed`). The editor uses `coroutineScope()` from `CoroutineExt.kt` for this collection; `DocumentListener` registered with `document.addDocumentListener(listener, this)` for auto-detach on dispose

11. **FlaiPipelineFileEditorProvider.kt + plugin.xml** — one-liner provider class; add `<fileEditorProvider>` extension. Deploy and smoke-test end to end.

---

## Test Strategy

**Unit tests (no IntelliJ platform):**

- `YamlPipelineSerializerTest`: round-trip all 7 gate types; verify `LlmGate` multi-line `promptTemplate` preserved as YAML literal block (`|`); verify default fields are omitted when matching defaults; verify edge `fromPort`/`toPort` only written when non-default.
- `GatePortsTest`: enumerate `inputPorts()` and `outputPorts()` for each gate subclass; `LogicGate` with two branches and a `defaultPort` → four output ports; `InputGate` → zero input ports.
- `VisualPipelineModelTest`: ID rename cascades to all edges referencing old ID in `from` or `to`; duplicate edge returns `false`; `removeNode` removes all incident edges; `toPipeline()` produces correct `PipelineEdge` list using resolved `GateId` strings.
- `VisualPipelineValidatorTest`: for each gate type, missing each required field in turn produces an error naming the gate ID and the field; EC-10 (edge with non-existent gate ID) produces error; EC-11 (edge with non-existent port) produces error; valid model produces empty error list.
- `PipelineAutoLayoutTest`: single-chain pipeline → nodes assigned distinct x positions in order; fork pipeline → no two nodes share the same (x, y); BFS rank of entry node is 0.

**Integration tests (IntelliJ platform test harness, marked `@Requires(PluginId)`):**
- Not required for MVP; the editor is sufficiently tested by unit tests plus manual smoke testing with `runIde`.

**Manual smoke-test checklist:**
- Open a `*.flai.yaml` → two tabs appear (Text / Visual).
- Canvas renders all gate types with distinct colours.
- Drag node → position changes; YAML unchanged; Apply → YAML updated.
- Drop palette gate → unique ID generated; gate appears unconnected.
- Connect two nodes by dragging port handles → edge appears.
- Delete edge via Delete key.
- Property panel: rename gate ID → edges cascade.
- Apply on invalid (blank LlmGate fields) → error banner.
- First Apply on a commented file → warning dialog appears once.
- Run from Visual tab → badges update in real time.
- Sidecar file created after Apply; positions restored on reopen; positions absent after sidecar deletion.

---

## Risks

**R1: `WriteCommandAction` + `DocumentListener` feedback loop**
The `DocumentListener` sets `externalEditFlag = true` on any document change, including the `WriteCommandAction` triggered by Apply. This would immediately mark the canvas as needing reload.
Mitigation: gate the flag with a boolean `applyInProgress`; set it to `true` before calling `WriteCommandAction` and `false` in the same EDT callback after the write completes. The listener skips flag-setting when `applyInProgress` is true.

**R2: Heavy canvas repaints on large pipelines**
`paintComponent` is called on every repaint including mouse move (edge-drag preview). With 50+ nodes the naive approach redraws all nodes on every event.
Mitigation: clip repaints to the dirty region for static content; for interactive drag/preview draw only the moving element and the edge-preview line. Use `repaint(x, y, w, h)` overload to limit repaint area during drag.

**R3: SnakeYAML multi-line string serialisation**
SnakeYAML's default `dump()` converts `\n` in strings to flow-style or escaped sequences. The hand-rolled serialiser must write `promptTemplate` as a YAML literal block scalar (`|`) when the value contains newlines.
Mitigation: `YamlPipelineSerializer` checks `contains('\n')` and emits `|` prefix + indented lines. `YamlPipelineSerializerTest` verifies a multi-line `promptTemplate` round-trips correctly.

**R4: Gate-label collision in execution overlay**
Two gates with the same `label` field will receive identical `GateRow.gateName` entries; both nodes will show the same badge.
Mitigation: this is an accepted cosmetic limitation. Document it in code comments on `PipelineCanvas.setOverlay()`. No structural fix in MVP.

**R5: VirtualFile detection for non-project `.flai.yaml` files**
`FlaiPipelineFileEditorProvider.accept()` matches any file ending in `.flai.yaml`, including files outside the project directory. `LayoutStore` needs a valid `.flai/` directory path.
Mitigation: in `FlaiPipelineFileEditor` constructor, derive sidecar path from `virtualFile.parent.path + "/" + virtualFile.name + ".layout.json"`. If `virtualFile.parent` is null or the file is read-only, `LayoutStore.save()` silently skips writing the sidecar.

---

## Questions for Business Analyst

_None_
