# Dev Report: UI Modernization

## Implemented

### New Files Created
- **`src/main/kotlin/me/drew/flai/ui/visual/FlaiEditorTheme.kt`** — Single source of truth for all accent colors (`JBColor` pairs for 7 gate types), canvas background, grid, node chrome, and port colors. Exposes `accentFor(Gate)`, `accentForType(String)`, and `GRID_SIZE = 20`.
- **`src/main/kotlin/me/drew/flai/ui/visual/MinimapPanel.kt`** — 160×100 px custom-painted `JPanel`; renders all node rectangles and edges at scale, draws a viewport indicator rectangle using the canvas view transform. `refresh()` triggers repaint.

### Modified Files

**`src/main/kotlin/me/drew/flai/ui/editor/FlaiIcons.kt`**
- Added 7 gate-type icon constants (`GATE_INPUT`, `GATE_OUTPUT`, `GATE_LLM`, `GATE_LOGIC`, `GATE_TOOL`, `GATE_READ_FILE`, `GATE_WRITE_FILE`) mapped to closest `AllIcons` equivalents.

**`src/main/kotlin/me/drew/flai/ui/visual/PipelineCanvas.kt`**
- Removed top-level `gateTypeColor(Gate)` function; all canvas code uses `FlaiEditorTheme.accentFor` / `FlaiEditorTheme.accentForType`.
- Dot-grid canvas background rendered in `paintComponent` before edges/nodes using `FlaiEditorTheme.GRID_DOT_COLOR`.
- Node card visual rework in `drawNode`: card background (`NODE_BG`), drop shadow (elevated on hover), left 4 px accent border, gate icon left of label, rounded corners, selection outer glow.
- Entry-gate star removed; replaced with filled orange triangle badge at top-left of node card.
- LLM-only 5-point star drawn at top-right of LLM node cards; `findLlmStarAt()` hit-tests a radius-8 circle.
- Hover state: `hoveredNodeSeq`, `nodeHoverAlpha` map, `hoverTimer` (60 ms); smooth alpha convergence in `tickHoverAlpha()`; timer stops when all alphas converge to 0.
- Port visuals: `FlaiEditorTheme.PORT_INPUT` / `PORT_OUTPUT` colors; glow ring on selected/hovered node.
- Port tooltips: `getToolTipText(MouseEvent)` returns `"Input: <name>"` / `"Output: <name>"`; registered with `ToolTipManager`.
- Ghost drag: `mouseDragged` accumulates `ghostDragOffsetX/Y`; model not mutated until `mouseReleased`; ghost shown as 45%-alpha accent-colored rounded rect at snapped position; Escape cancels and restores original position.
- Snap-to-grid: `snapToGrid(value, gridSize)` top-level function; applied on `mouseReleased` drop.
- `zoomLocked` field; guards `mouseWheelMoved`, `zoomIn()`, `zoomOut()`.
- `zoomIn()` and `zoomOut()` public methods zoom around canvas center.
- `onLlmStarClicked: ((VisualNode) -> Unit)` callback; fired in `mousePressed` after selection.
- `onRepaint: (() -> Unit)` callback; invoked at end of `paintComponent` (drives minimap refresh).
- Edge brightening in `drawEdge`: edges connected to selected or hovered node use a lighter/brighter color.

**`src/main/kotlin/me/drew/flai/ui/visual/GatePalettePanel.kt`**
- Constructor changed to `GatePalettePanel(project: Project, parentDisposable: Disposable)`.
- Replaced `JButton` palette items with `GatePillPanel` inner class: `JPanel` with `setOpaque(false)`, custom `paintComponent` draws rounded accent-colored background; gate icon + label (or icon-only when collapsed).
- `filterGateTypes(query, types)` extracted as top-level function for unit testability.
- Search `JTextField` at top; real-time filter on document changes; "No results" `JLabel` when filter is non-empty and no match.
- Collapse toggle button; `isCollapsed` property persisted via `PropertiesComponent.getInstance(project)` keyed `"flai.palette.collapsed.<project.locationHash>"`.
- `onCollapseToggled: ((Boolean) -> Unit)` callback exposed for splitter proportion updates.
- `LafManagerListener` registered on application message bus (not project bus — see Deviations); connection disposed via `Disposer.register(parentDisposable, connection)`.
- Drag-and-drop wired onto `GatePillPanel` via `TransferHandler` + `MouseMotionAdapter`.
- Clicking a collapsed palette expands it and focuses the search field.

**`src/main/kotlin/me/drew/flai/ui/visual/NodePropertyPanel.kt`**
- `JTable` + `+/-` mapping implementation replaced with per-row `JPanel` list: two `JTextField` + trash `JButton` per row; empty-state `JLabel` when list is empty; `"+ Add"` button appends a row. Wrapped in titled card panels.
- `scrollToLlmFieldGroup()` added: scrolls viewport to `firstLlmField` bounds and requests focus.
- Empty state replaced with a styled panel: `AllIcons.General.Information` icon + message label centered and grayed.
- All gate-specific field groups wrapped in titled `cardPanel(...)` panels (bold 13pt title font):
  - `InputGate` → "Schema" card
  - `OutputGate` → "Output Mapping" card
  - `LlmGate` → "Prompt Template" card, "Endpoint Config" card, "Skills" card, "Input Mapping" card, "Output Mapping" card
  - `LogicGate` → "Branches" card
  - `ToolGate` → "Tool Settings" card, "Input Mapping" card, "Output Mapping" card
  - `ReadFileGate` / `WriteFileGate` → "File Settings" card (was already done)
- `labeledRow` row height: `maximumSize` height 32 px (unchanged from prior impl; was already 32).
- Blue focus ring on `JTextField` via `addFocusListener` (compound border on focus gained, restored on lost); also applied to LlmGate's `JTextArea` prompt field.
- `preferredSize = Dimension(280, 400)` and `minimumSize = Dimension(220, 200)` set on panel.
- Dead-code cleanup: removed private `addRow`, `addSection`, `addSeparator` methods (no longer called after card grouping).

**`src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditor.kt`**
- Canvas wrapped in `JLayeredPane` (`canvasLayer`); canvas at `DEFAULT_LAYER`; transparent overlay `JPanel` (null layout) at `PALETTE_LAYER`.
- Zoom button panel (zoom in / zoom out / reset / lock toggle) added to overlay at top-right with absolute bounds; `lockZoomBtn` toggles `canvas.zoomLocked` and enables/disables other zoom buttons.
- `MinimapPanel` added to overlay at bottom-left.
- `ComponentListener` on `canvasLayer` resizes canvas and repositions overlay children on every resize.
- `mainSplit` stored as `lateinit var` field; initial proportion driven by `palettePanel.isCollapsed`.
- `palettePanel.onCollapseToggled` wired to update `mainSplit.proportion` (0.06f collapsed / 0.18f expanded).
- `GatePalettePanel` constructor now receives `project` and `this` (as `parentDisposable`).
- `canvas.onRepaint = { minimapPanel.refresh() }` wired.
- `canvas.onLlmStarClicked` wired: selects node if not already selected, then calls `propertyPanel.scrollToLlmFieldGroup()`.
- `runBtn` rendered with `FlaiIcons.GUTTER_RUN` icon; tooltips added to all toolbar buttons.
- Toolbar `preferredSize` height set to 38 px; horizontal `JSeparator` added at `BorderLayout.SOUTH` of `topBar`.

### Test Files Created
- **`src/test/kotlin/me/drew/flai/ui/visual/FlaiEditorThemeTest.kt`** — 6 unit tests: `accentFor` and `accentForType` return non-null and distinct colors for all 7 gate types; fallback for unknown type; `GRID_SIZE` positive.
- **`src/test/kotlin/me/drew/flai/ui/visual/PaletteFilterTest.kt`** — 8 unit tests for `filterGateTypes`: empty query, exact match, partial match, case-insensitive, no match, hyphen type, file types, empty input list.
- **`src/test/kotlin/me/drew/flai/ui/visual/SnapToGridTest.kt`** — 6 unit tests for `snapToGrid`: exact grid, below midpoint, at midpoint, above midpoint, gridSize=1, negative values.

## Deviations

1. **`LafManagerListener` registered on application bus, not project bus.** The architecture doc says `project.messageBus`. LAF switching is application-scoped (not per-project), so `ApplicationManager.getApplication().messageBus.connect()` is the correct API. Using `project.messageBus` would not receive LAF change events. `Disposer.register(parentDisposable, connection)` still correctly cleans up on editor disposal.

2. **`FlaiEditorTheme` constant named `CANVAS_BG` instead of `CANVAS_BG_DARK`.** The architecture doc's Data Model section shows `CANVAS_BG_DARK`; however naming it `_DARK` is misleading since `JBColor` resolves to the light value in light themes. The implementation uses the more accurate `CANVAS_BG`.

3. **`NODE_BG` instead of `NODE_BG_DARK`.** Same reasoning as deviation 2.

4. **`buildSkillsSection` refactored to `buildSkillsCard` returning `JPanel`.** The original helper added directly to `innerPanel`; the refactored version returns the card panel for inline `innerPanel.add(...)` consistency with the other card builders. The behavior is identical.

5. **`GatePalettePanel.isCollapsed` initial value loaded from `PropertiesComponent` at field init, not in `buildUI()` before construction.** The architecture doc suggests restoring collapse state in `FlaiPipelineFileEditor.buildUI()` "before `buildUI()` is called." Since `palettePanel` is constructed in `init` (before `buildUI()`), the collapse state is read inside the `GatePalettePanel` constructor via `PropertiesComponent.getInstance(project).getBoolean(persistKey(), false)`. `FlaiPipelineFileEditor.buildUI()` then reads `palettePanel.isCollapsed` to set the initial splitter proportion. The net behavior matches the spec requirement.

6. **`buildLogicGateFields` "Default Port" row not wrapped in a card.** The spec's card table shows `LogicGate` cards as "Basic Info" and "Branches." "Default Port" is logically a basic-info field and is rendered as a bare labeled row adjacent to the Basic Info card. This avoids adding a one-field card for a field that conceptually belongs to basic info.

## Follow-ups

- **Manual testing required.** Integration and visual tests listed in the architecture doc's Test Strategy cannot be automated without `runIde`. The following manual checks should be performed in the sandbox IDE via `./gradlew runIde`:
  - All 7 gate types render with distinct accent colors in Darcula and IntelliJ Light.
  - Switching LAF while editor is open refreshes palette and canvas.
  - Minimap reflects viewport position when panning.
  - Zoom lock: scroll-wheel and overlay buttons are inert; lock button unlocks.
  - Ghost drag: drag node, see ghost; press Escape, node returns to origin.
  - LLM star click: selects node and scrolls properties panel to Prompt Template.
  - Empty mapping state; `+ Add` adds a row; trash removes it.
  - Palette search, collapse, and persistence across editor re-open.
  - Port tooltips, toolbar button tooltips, palette item tooltips.

- **Node move is not undoable.** The architecture doc acknowledges this in R1: `model.moveNode` is not registered as an undoable edit. Ghost drag defers the move to `mouseReleased` but does not add undo support. This is out of scope for this release.

- **Minimap performance on large pipelines.** Per R2: if latency is observed during drag (minimap `refresh()` called every repaint), throttle to one repaint per 50 ms. Not implemented proactively; monitor in practice.

## Questions for Architect

_None_
