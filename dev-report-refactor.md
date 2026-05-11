# Dev Report: UI Visual/Editor Encapsulation Refactor

## Implemented

Executed all 8 steps of the approved plan. Build is green and all 152 tests pass.

**Files modified:**

- `src/main/kotlin/me/drew/flai/ui/visual/VisualNode.kt` — Changed `gateId`, `gate`, `x`, `y` from `var` to `val`.
- `src/main/kotlin/me/drew/flai/ui/visual/VisualPipelineModel.kt` — Added private `_nodes`/`_edges` backing lists with read-only `nodes`/`edges` views. Made `pipelineId`, `pipelineName`, `pipelineDescription`, `entryNodeSeq`, `isDirty` use `private set`. Added `clearDirty()`, `setEntry()`, `replaceWith()`. Updated all internal mutating methods (`moveNode`, `updateGate`, `renameGateId`, `removeNode`, `undo`) to use `_nodes[idx].copy(...)` instead of direct field assignment.
- `src/main/kotlin/me/drew/flai/ui/visual/PipelineCanvas.kt` — Added `PipelineCanvasListener` interface at top of file. Removed `onNodeSelected`, `onLlmStarClicked`, `onRepaint` public var lambdas; replaced with `_listener`/`setListener()`. Made `isEditable`, `executionStatus`, `zoomLocked` private with setter methods. Added `private val renderer = CanvasRenderer()`. Rewrote `paintComponent` to build `CanvasRenderState` snapshot and delegate to `renderer.paint()`. Kept `drawDotGrid` in canvas (uses screen-space transform). Updated `handleRightClick` to call `model.setEntry()`. Made constants `internal`. Updated `findOutputPortAt`, `findEdgeAt`, `getToolTipText` to call `renderer.outputPortY()`, `renderer.outputPortCenter()`, `renderer.inputPortCenter()`. Added a second `dist` overload to handle `Double`/`Int` mix used by `findLlmStarAt`.
- `src/main/kotlin/me/drew/flai/ui/visual/GatePalettePanel.kt` — Made `isEditable` private with `setEditable()` method. Made `onCollapseToggled` private with `setOnCollapseToggled()`. Made `isCollapsed` setter private.
- `src/main/kotlin/me/drew/flai/ui/visual/NodePropertyPanel.kt` — Rewrote to delegate all gate-type section building to `GatePropertySections`. Made `isEditable` private with `setEditable()`. Removed `firstLlmField` and replaced with `firstFocusTarget` from `SectionResult`. Removed private `updateGate`, `rebuildWithLabel`, `buildTextField`, `cardPanel`, `labeledRow` — all moved to `GatePropertySections`.
- `src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditor.kt` — Replaced three `canvas.onXxx =` assignments with `canvas.setListener(object : PipelineCanvasListener { ... })`. Updated all property assignments to use new setter methods (`setEditable`, `updateExecutionStatus`, `setZoomLocked`). Updated `palettePanel.onCollapseToggled =` to `setOnCollapseToggled(...)`. Replaced `model.isDirty = false` with `model.clearDirty()`. Updated `reloadFromDocument` to use `model.replaceWith(newModel)`. Fixed `resetZoomBtn` click handler to check `lockZoomBtn.isSelected` instead of the now-private `canvas.zoomLocked`.

**Files created:**

- `src/main/kotlin/me/drew/flai/ui/visual/CanvasRenderer.kt` — New file containing `CanvasRenderState` data class and `CanvasRenderer` class. Renderer owns all draw methods (`drawNode`, `drawEdge`, `drawPort`, `drawLlmStar`, `drawStatusBadge`, `drawBezier`, `drawDotGrid`-related helpers), geometry utilities (`outputPortCenter`, `inputPortCenter`, `outputPortY`), and helper methods (`logicBranchColor`, `logicPortLabel`, `gateIcon`).
- `src/main/kotlin/me/drew/flai/ui/visual/GatePropertySections.kt` — New file containing `SectionResult` data class and `GatePropertySections` class. Provides `buildBasicInfoFields`, `buildInputGateFields`, `buildLlmGateFields`, `buildLogicGateFields`, `buildToolGateFields`, `buildOutputGateFields`, `buildReadFileGateFields`, `buildWriteFileGateFields`, `buildMappingSection`. Also contains shared helpers `buildTextField`, `labeledRow`, `cardPanel`.

**Test files updated:**

- `src/test/kotlin/me/drew/flai/ui/visual/VisualPipelineModelTest.kt`
- `src/test/kotlin/me/drew/flai/ui/visual/VisualPipelineValidatorTest.kt`

## Deviations

1. **Test rewrite (forced)**: The plan's `private set` on `pipelineId`, `pipelineName`, `entryNodeSeq`, `isDirty` and the immutable `nodes`/`edges` views caused compile failures in the test helpers. Tests directly assigned `model.pipelineId = "test"`, `model.entryNodeSeq = seq`, `model.isDirty = false`, and called `model.edges.add(...)`. Updated both test files to use `VisualPipelineModel.fromPipeline(...)` for setup, `setEntry()`/`clearDirty()` for mutation, and `addEdge()` for adding edges.

2. **Inline gate blocks extracted into dedicated methods**: The plan enumerated `buildInputGateFields`, `buildLlmGateFields`, `buildLogicGateFields`, `buildToolGateFields` as the methods to extract. The original `showGate` also had inline blocks for `OutputGate`, `ReadFileGate`, and `WriteFileGate`. These were extracted into `buildOutputGateFields`, `buildReadFileGateFields`, `buildWriteFileGateFields` for consistency, rather than keeping them inline in `NodePropertyPanel.showGate`.

3. **`onLlmStarClicked` listener simplified**: The original editor lambda called `canvas.onNodeSelected(node)` then `propertyPanel.showGate(...)` when the clicked node was not yet selected. After the refactor, the canvas itself fires `_listener?.onNodeSelected(...)` before `_listener?.onLlmStarClicked(...)` in the star-click code path, so `onNodeSelected` (which calls `showGate`) runs first. The `onLlmStarClicked` override now only calls `propertyPanel.scrollToLlmFieldGroup()`. Behavior is preserved because `showGate` sets `firstFocusTarget` before `scrollToLlmFieldGroup` reads it.

4. **`dist()` overload added in PipelineCanvas**: `findLlmStarAt` compares against `Double` coordinates from mouse position and `Int` coordinates from node fields. Added a second `dist(Double, Double, Int, Int)` overload to avoid ambiguous implicit conversions.

## Follow-ups

None. No new environment variables, migrations, or config changes required.

## Questions for Architect

_None_
