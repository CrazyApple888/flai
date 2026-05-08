# Dev Report: Visual Pipeline Editor

## Implemented

### Infrastructure
- `YamlPipelineSerializer` — hand-rolled block-style YAML serializer mirroring `YamlPipelineParser` key ordering. Handles all 7 gate types, multiline `promptTemplate` via block scalar (`|`), safe scalar quoting (reserved words, special chars).
- `YamlPipelineSerializerTest` — round-trip tests for all gate types.

### Domain helpers
- `GatePorts.kt` — `Gate.inputPorts()` and `Gate.outputPorts()` extension functions. `InputGate` has no input ports; `LogicGate` outputs = `branches.map{it.port} + listOfNotNull(defaultPort)`.

### UI visual package (`ui/visual/`)
- `VisualNode` / `VisualEdge` — immutable model objects with stable `nodeSeq` identity separate from mutable `gateId`.
- `VisualPipelineModel` — mutable editing state: nodes, edges, positions, entry. `toPipeline()` converts back to domain model. `fromPipeline()` factory builds from domain + sidecar positions.
- `LayoutStore` — loads/saves sidecar `.layout.json` file co-located with `.flai.yaml`. Uses SnakeYAML for reading (JSON is valid YAML); hand-rolled JSON for writing.
- `PipelineAutoLayout` — topological rank BFS layout. Groups gates by longest-path rank, assigns `x = rank * 220`, `y` centered per column.
- `VisualPipelineValidator` — validates required fields per gate type, pipeline metadata, and edge port integrity.
- `GatePalettePanel` — 7 gate type buttons with `TransferHandler` drag support. Colors match canvas node colors.
- `PipelineCanvas` — `JPanel` with `Graphics2D` rendering: nodes (rounded rect, color by type, entry marker star), bezier edges, port circles. Full mouse interaction: click/drag nodes, drag-from-port edge creation, pan (empty space drag), zoom (mouse wheel, 0.3–2.5x), right-click context menu (Delete, Set as Entry), Delete key for selected edges. `AffineTransform` for pan/zoom with inverse hit-testing. Execution status overlay (RUNNING pulsing blue, SUCCESS green, FAILURE red badges). `resetTransform()` fits all nodes.
- `NodePropertyPanel` — per-gate-type property forms. Common fields: ID (calls `model.renameGateId`), Label. Gate-specific: InputGate schema table, OutputGate/LlmGate/ToolGate mapping tables, LlmGate prompt textarea + skills list + endpoint fields, LogicGate branches with add/delete per row, ToolGate toolName dropdown from `IdeToolRegistry` (falls back to text field), ReadFileGate/WriteFileGate simple fields.

### Editor package (`ui/editor/`)
- `FlaiPipelineFileEditorProvider` — `FileEditorProvider` + `DumbAware`. Accepts `*.flai.yaml`. Policy: `PLACE_AFTER_DEFAULT_EDITOR`.
- `FlaiPipelineFileEditor` — `FileEditor` implementation. Layout: `OnePixelSplitter(0.13)` palette | `OnePixelSplitter(0.75)` canvas | property panel. Toolbar: Apply, Auto-layout, Fit, Run, Cancel. `DocumentListener` with 300ms debounce for text→visual sync. Apply: validates, one-time YAML-comment warning dialog (per file, persisted via `PropertiesComponent`), serializes, `WriteCommandAction`, saves sidecar. Subscribes read-only to `FlaiPipelineUiService.executionState` and `logRows` to drive execution overlay and lock editing during run.

### plugin.xml
- Added `<fileEditorProvider>` extension point for `FlaiPipelineFileEditorProvider`.

## Deviations

- **Node model mutation during rebuild**: `reloadFromDocument()` mutates the existing `VisualPipelineModel` in-place rather than replacing it, to preserve the `canvas` reference. Nodes list is cleared and refilled.
- **`MutableList` on `VisualPipelineModel`**: The architect doc used callbacks; the implementation uses direct mutation + `canvas.repaint()` calls via `NodePropertyPanel.updateGate()`. Simpler and sufficient for single-editor use.
- **Branch save logic in `NodePropertyPanel`**: Branch condition save reads panel sub-components by position index. This is fragile but avoids introducing a separate branch state class.
- **`FileEditorState`**: Returns a no-op lambda; no state is persisted across IDE restarts. Acceptable for MVP.
- **Coroutine scope not via `CoroutineExt`**: `FlaiPipelineFileEditor` creates its own `CoroutineScope` and cancels in `dispose()` directly, since `FileEditor` doesn't implement `Disposable` and can't use the `Disposable.coroutineScope()` extension.

## Follow-ups

1. **Manual verification required**: Run `./gradlew runIde`, open a `.flai.yaml` file, verify the "Visual" tab appears alongside the YAML tab. Test drag/connect/apply flow end-to-end.
2. **Branch condition save fragility**: The branch condition panel reads sub-components by index from `condDetailPanel.components`. If the panel layout changes, saves will break. Should be refactored to use explicit field references.
3. **Multi-select** deferred to Phase 2 per PM decision.
4. **Undo/redo**: Visual edits have no fine-grained undo. IntelliJ's standard Ctrl+Z after Apply will undo the YAML text change, but intermediate visual operations are not undoable.
5. **Pipeline metadata editing**: Canvas background click does not currently open a pipeline metadata panel — only gate nodes open the property panel. This was specified in the PM doc (item 15) but not implemented; add a "Pipeline Properties" button to the toolbar as a follow-up.
6. **Sidecar gitignore**: The PM doc leaves this as an open decision. Users may want to add `.flai/*.layout.json` to `.gitignore`.

## Questions for Architect

_None_
