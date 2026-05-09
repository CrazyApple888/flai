# Architecture: Visual Pipeline Editor UI Modernization

## Overview

This feature is a UI-only refresh of the visual pipeline editor introduced in the `visual-pipeline-editor` feature. No domain model, YAML schema, pipeline execution logic, or data structures change.

Layers touched:
- `ui/visual/` — all five files that implement the canvas, palette, properties panel, and supporting types
- `ui/editor/FlaiPipelineFileEditor.kt` — toolbar rebuild, layout proportions, and palette collapse wiring
- New shared file `ui/visual/FlaiEditorTheme.kt` — single source of truth for accent colors (FR-12)
- New file `ui/visual/MinimapPanel.kt` — canvas overlay minimap component
- `ui/editor/FlaiIcons.kt` — gate-type icon registry

The hexagonal architecture boundary is respected: no `domain/`, `infrastructure/`, or `usecase/` files are modified.

---

## Tech Decisions

**Decision:** Extract all accent colors into a single `FlaiEditorTheme` object.  
**Reason:** `gateTypeColor()` is currently duplicated between `PipelineCanvas.kt` (line 24) and `GatePalettePanel.kt` (line 72). FR-12 is explicitly the single source of truth for both dark and light palettes. A single object prevents drift. Every consumer (canvas, palette, property panel cards) imports from there.

**Decision:** Replace the `JTable` + `+/-` button pair for mapping rows with a custom per-row panel list.  
**Reason:** FR-50/FR-51 require an `+ Add` button and a per-row trash icon. `JTable` selection-then-remove does not model this; each row must be an independently removable component. Each row is rendered as a `JPanel` (BoxLayout, horizontal) containing two `JTextField` instances and a `JButton` (trash icon). The list of rows is held in a `JPanel` with `BoxLayout(Y_AXIS)` and rebuilt on add/remove. The `JTable` and `+/-` buttons in `buildMappingSection` are removed entirely.

**Decision:** Defer `model.moveNode()` calls until `mouseReleased` during node drag; hold a ghost overlay position in canvas state during the drag.  
**Reason:** Currently `model.moveNode` is called every `mouseDragged` tick (line 157–162 of `PipelineCanvas.kt`), which moves the node in real time. FR-24/FR-25/FR-26 require (a) the node to remain at its original position until dropped, (b) a semi-transparent ghost to follow the cursor, and (c) the drop position to snap to the nearest grid point. This means the drag must be decoupled from model mutation. The canvas holds the ghost's screen-space position during drag and only writes to the model at release (snapped).

**Decision:** Implement hover state transitions via a `javax.swing.Timer`-driven alpha field per node, not a single global animation counter.  
**Reason:** FR-37 requires smooth per-node hover transitions. The existing `animTimer` drives a single `animTick` counter used only for running-status pulse. Hover transitions need an independent per-node `hoverAlpha: Float` that increments/decrements toward 0.0/1.0 on each timer tick. A single shared `hoverTimer` (60 ms interval) drives all nodes' alpha convergence and calls `repaint()`. This avoids one timer per node while keeping transitions independent.

**Decision:** Implement canvas tooltips by overriding `getToolTipText(MouseEvent)` on `PipelineCanvas` and registering with `ToolTipManager`.  
**Reason:** Port handles are custom-painted regions, not Swing components, so `setToolTipText()` on a child widget is not available. The correct Swing mechanism for region-based tooltips on a single component is `getToolTipText(MouseEvent)` + `ToolTipManager.sharedInstance().registerComponent(this)`.

**Decision:** Use `PropertiesComponent.getInstance(project)` to persist palette collapse state.  
**Reason:** The same API is already used for the "apply warning" preference in `FlaiPipelineFileEditor.kt` (line 199). Introducing a new persistence mechanism would add unnecessary dependency. Key: `"flai.palette.collapsed.<project.locationHash>"`.

**Decision:** Use `LafManagerListener` to detect IDE theme switches at runtime and repaint the canvas and palette.  
**Reason:** `JBColor` resolves dynamically at paint time for custom-painted canvas elements, so those are fine. However, palette items whose `background` is set via `setBackground(JBColor(...))` resolve once at construction. Registering a `LafManagerListener` on the project's message bus allows a refresh: palette re-creates its item panels, canvas calls `repaint()`. If full live switching is not feasible, the doc states behavior is "update on next editor open" — but the architecture supports live update and the developer should implement it.

**Decision:** Remove the existing entry-gate top-right star in `PipelineCanvas.drawNode` and replace with a distinct left-edge triangle badge.  
**Reason:** The PM doc assigns the top-right star exclusively to LLM gates (FR-33). Entry gate gets a "visual refresh" (FR-32) but not a star. The existing `drawStar()` call at line 411 is attached to `isEntry`, which conflicts. The entry marker becomes a small filled orange triangle drawn at the top-left corner of the node card. The `drawStar()` function is repurposed to the LLM-only star (FR-33/FR-34), which must be a clickable hit-region.

**Decision:** No keyboard zoom shortcut exists today, so zoom-lock keyboard blocking is a no-op.  
**Reason:** The current canvas only handles scroll-wheel zoom (`mouseWheelMoved`, line 206). No keyboard zoom is registered. The BA edge case about keyboard zoom shortcuts is explicitly out of scope for this implementation.

**Decision:** Grid snap uses `GRID_SIZE = 20` (px in model space).  
**Reason:** NODE_WIDTH=140, NODE_HEIGHT=60. A 20 px grid aligns cleanly with node dimensions and gives enough granularity for dense pipelines. This is an architect-level aesthetic constant, not a user preference (the PM doc rules out a theming engine).

**Decision:** Canvas overlay controls (zoom buttons, minimap) use a `JLayeredPane` wrapper around `PipelineCanvas`.  
**Reason:** `PipelineCanvas` uses custom `paintComponent` rendering. Adding Swing child components directly to it with a `null` layout is possible but fragile because `PipelineCanvas` does not manage child component positions. The clean solution is: `FlaiPipelineFileEditor` wraps `canvas` in a `JLayeredPane`; the canvas occupies `JLayeredPane.DEFAULT_LAYER`; a transparent overlay `JPanel` (null layout, not opaque) occupies `JLayeredPane.PALETTE_LAYER`; zoom buttons and minimap are added to the overlay panel with absolute `setBounds()`. The overlay panel is resized to match the layer pane via a `ComponentListener` on the layer pane. Zoom controls sit in the **top-right** corner (conventional for canvas editors); minimap sits in the **bottom-left** corner as per FR-20.

**Decision:** Minimap refresh is driven by a canvas repaint callback rather than by overriding `repaint()`.  
**Reason:** `repaint()` is called from many internal Swing paths and cannot be cleanly overridden. Instead, `PipelineCanvas` exposes `var onRepaint: (() -> Unit) = {}`. At the end of `paintComponent`, after `g2.transform = savedTransform`, `onRepaint()` is invoked. `FlaiPipelineFileEditor` sets `canvas.onRepaint = { minimapPanel.refresh() }`. `MinimapPanel.refresh()` calls its own `repaint()`.

**Decision:** `onLlmStarClicked` is fired only when a star click occurs; selection logic is handled inside `PipelineCanvas.mousePressed` before the callback.  
**Reason:** FR-34 states "clicking the star on an already-selected LLM node scrolls to LLM fields without deselecting and reselecting." If the wiring is `canvas.onNodeSelected(node); propertyPanel.scrollToLlmFieldGroup()` unconditionally, `NodePropertyPanel.showGate` tears down and rebuilds its inner panel (lines 38–97) on every call — which loses scroll position and focus. Instead: `PipelineCanvas.mousePressed` selects the node only if it is not already `selectedNodeSeq`. `onLlmStarClicked` is always fired (node already selected or just selected). `FlaiPipelineFileEditor` wires it as `canvas.onLlmStarClicked = { node -> if (canvas.getSelectedNode()?.nodeSeq != node.nodeSeq) { canvas.onNodeSelected(node) }; propertyPanel.scrollToLlmFieldGroup() }`.

**Decision:** `OnePixelSplitter` proportion for the palette is updated programmatically on collapse/expand toggle.  
**Reason:** When the palette collapses to icon-only, it needs to shrink its allocated width (FR-14). `OnePixelSplitter` does not auto-adjust when a child changes size. `GatePalettePanel` exposes an `onCollapseToggled: ((Boolean) -> Unit)` callback. `FlaiPipelineFileEditor` sets this callback to update `mainSplit.proportion`: collapsed → `0.06f`; expanded → `0.18f` (or whatever fits the icon-only vs. full-label widths). The `mainSplit` reference must be stored as a field.

**Decision:** `GatePalettePanel` takes a `parentDisposable: Disposable` constructor parameter for `LafManagerListener` cleanup.  
**Reason:** The `LafManagerListener` connection registered on `project.messageBus` must be released when the editor is disposed (R3). Passing the `FlaiPipelineFileEditor` instance (which implements `Disposable`) as `parentDisposable` to `GatePalettePanel` allows `Disposer.register(parentDisposable, connection)` where `connection` is the `MessageBusConnection`. `GatePalettePanel` constructor signature becomes `class GatePalettePanel(private val project: Project, parentDisposable: Disposable)`.

**Decision:** Remove the `onLlmFieldGroupVisible` parameter from `NodePropertyPanel` constructor; use only the public `scrollToLlmFieldGroup()` method.  
**Reason:** The constructor callback is over-engineered; the caller only needs to invoke `scrollToLlmFieldGroup()` after the gate is shown. `NodePropertyPanel` constructor remains `class NodePropertyPanel(private val toolRegistry: IdeToolRegistry)`.

---

## Data Model

No domain model changes. The following UI-layer additions exist only in the canvas state:

```kotlin
// Inside PipelineCanvas — new drag/hover state fields
private var ghostDragOffsetX: Int = 0      // model-space delta during ghost drag
private var ghostDragOffsetY: Int = 0
private var isGhostDragging: Boolean = false
private var originalNodeX: Int = 0         // position before drag began
private var originalNodeY: Int = 0
private var zoomLocked: Boolean = false
private var hoveredNodeSeq: Int = -1
private val nodeHoverAlpha: MutableMap<Int, Float> = mutableMapOf()  // seq -> 0..1

// Single hover animation timer (replaces nothing; kept separate from animTimer)
private val hoverTimer = Timer(60) { tickHoverAlpha(); repaint() }
```

```kotlin
// Inside GatePalettePanel — new state
private var isCollapsed: Boolean = false     // persisted via PropertiesComponent
private var searchText: String = ""          // live filter
```

```kotlin
// FlaiEditorTheme.kt (new)
object FlaiEditorTheme {
    const val GRID_SIZE = 20            // model-space px

    // Accent: light hex, dark hex
    val INPUT   = JBColor(0x3DAB5C, 0x2D7A42)
    val OUTPUT  = JBColor(0xE07B20, 0xB85F10)
    val LLM     = JBColor(0x3375C8, 0x2259A0)
    val LOGIC   = JBColor(0xC8A800, 0x967D00)
    val TOOL    = JBColor(0x8B44C8, 0x6A2EA0)
    val READ_FILE  = JBColor(0xC89200, 0x997000)
    val WRITE_FILE = JBColor(0xC83050, 0xA0243E)

    // Canvas backgrounds
    val CANVAS_BG_DARK  = JBColor(0xF5F5F5, 0x1E1E1E)
    val GRID_DOT_COLOR  = JBColor(Color(0, 0, 0, 22), Color(255, 255, 255, 22))

    // Node chrome
    val NODE_BG_DARK    = JBColor(0xFFFFFF, 0x2D2D2D)
    val NODE_SHADOW     = JBColor(Color(0,0,0,35), Color(0,0,0,80))
    val NODE_SELECTED_GLOW = JBColor(Color(55,120,255,60), Color(80,140,255,80))
    val SELECTION_OUTLINE  = JBColor(Color(55,120,255), Color(90,150,255))
    val PORT_INPUT      = JBColor(Color(100,115,160), Color(110,130,190))
    val PORT_OUTPUT     = JBColor(Color(60,170,90),  Color(60,190,90))

    fun accentFor(gate: Gate): Color = when (gate) {
        is InputGate    -> INPUT
        is OutputGate   -> OUTPUT
        is LlmGate      -> LLM
        is LogicGate    -> LOGIC
        is ToolGate     -> TOOL
        is ReadFileGate -> READ_FILE
        is WriteFileGate -> WRITE_FILE
    }

    fun accentForType(gateType: String): Color = when (gateType) {
        "input"      -> INPUT
        "output"     -> OUTPUT
        "llm"        -> LLM
        "logic"      -> LOGIC
        "tool"       -> TOOL
        "read-file"  -> READ_FILE
        "write-file" -> WRITE_FILE
        else -> JBColor(0xDDDDDD, 0x444444)
    }
}
```

Gate-type icons — stored as `AllIcons` aliases or resource SVGs registered in `FlaiIcons`:

```kotlin
object FlaiIcons {
    val PIPELINE_FILE: Icon = AllIcons.FileTypes.Yaml
    val GUTTER_RUN:    Icon = AllIcons.Actions.Execute
    // Gate-type icons (mapped to closest AllIcons equivalents)
    val GATE_INPUT:     Icon = AllIcons.Actions.Download
    val GATE_OUTPUT:    Icon = AllIcons.Actions.Upload
    val GATE_LLM:       Icon = AllIcons.Actions.Lightning          // or AllIcons.Nodes.Function
    val GATE_LOGIC:     Icon = AllIcons.Actions.SplitVertically
    val GATE_TOOL:      Icon = AllIcons.Nodes.Plugin
    val GATE_READ_FILE: Icon = AllIcons.Actions.MenuOpen
    val GATE_WRITE_FILE:Icon = AllIcons.Actions.MenuSaveAll
}
```

---

## APIs / Interfaces

### FlaiEditorTheme (new — `ui/visual/FlaiEditorTheme.kt`)

```kotlin
object FlaiEditorTheme {
    const val GRID_SIZE: Int
    val INPUT: JBColor; val OUTPUT: JBColor; val LLM: JBColor
    val LOGIC: JBColor; val TOOL: JBColor
    val READ_FILE: JBColor; val WRITE_FILE: JBColor
    val CANVAS_BG_DARK: JBColor; val GRID_DOT_COLOR: JBColor
    val NODE_BG_DARK: JBColor; val NODE_SHADOW: JBColor
    val NODE_SELECTED_GLOW: JBColor; val SELECTION_OUTLINE: JBColor
    val PORT_INPUT: JBColor; val PORT_OUTPUT: JBColor

    fun accentFor(gate: Gate): Color
    fun accentForType(gateType: String): Color
}
```

### PipelineCanvas — modified public surface

```kotlin
// Existing
var isEditable: Boolean
var onNodeSelected: ((VisualNode?) -> Unit)
var executionStatus: Map<String, GateStatus>
fun resetTransform()
fun getSelectedNode(): VisualNode?

// New
var zoomLocked: Boolean                // drives FR-23 zoom-lock
var onLlmStarClicked: ((VisualNode) -> Unit)  // FR-34: wired in FlaiPipelineFileEditor
var onRepaint: (() -> Unit)            // called at end of paintComponent; drives minimap refresh
fun zoomIn()                           // called by canvas zoom-in overlay button
fun zoomOut()                          // called by canvas zoom-out overlay button
```

### GatePalettePanel — modified public surface

```kotlin
// Constructor (new signature)
class GatePalettePanel(project: Project, parentDisposable: Disposable)

// Existing
var isEditable: Boolean

// New
var isCollapsed: Boolean                       // persisted; setter fires onCollapseToggled
var onCollapseToggled: ((Boolean) -> Unit)     // FlaiPipelineFileEditor updates mainSplit.proportion
```

### MinimapPanel (new — `ui/visual/MinimapPanel.kt`)

```kotlin
class MinimapPanel(
    private val model: VisualPipelineModel,
    private val getViewTransform: () -> AffineTransform,  // canvas supplies its current transform
    private val getCanvasSize: () -> Dimension,
) : JPanel() {
    // Repaints itself; caller must call refresh() whenever canvas repaints
    fun refresh()
    override fun paintComponent(g: Graphics)
    // Fixed preferred size: 160 x 100 px
}
```

### NodePropertyPanel — modified internal surface

```kotlin
// Constructor signature unchanged
class NodePropertyPanel(private val toolRegistry: IdeToolRegistry) : JPanel(BorderLayout())

// New public method for FR-34
// Scrolls the inner scrollPane to the Y-offset of the LLM fields section
// and calls requestFocusInWindow() on the first LLM text field.
// Safe to call even if the panel currently displays a non-LLM node (no-op in that case).
fun scrollToLlmFieldGroup()
```

### Card layout per gate type

| Gate type | Visible cards |
|---|---|
| `InputGate` | Basic Info, Schema |
| `OutputGate` | Basic Info, Output Mapping |
| `LlmGate` | Basic Info, Prompt Template, Endpoint Config, Skills, Input Mapping, Output Mapping |
| `LogicGate` | Basic Info, Branches |
| `ToolGate` | Basic Info, Tool Settings, Input Mapping, Output Mapping |
| `ReadFileGate` | Basic Info, File Settings |
| `WriteFileGate` | Basic Info, File Settings |

"Basic Info" always contains the ID and Label fields. "Schema", "Branches", "Prompt Template", "Endpoint Config", "Skills", "File Settings", and "Tool Settings" are additional cards that appear only for the gate types that have them. "Input Mapping" and "Output Mapping" appear as named in the table above. Each card is a `JPanel` with a titled `TitledBorder` using the semibold font from FR-55.

---

## File-Level Plan

- `src/main/kotlin/me/drew/flai/ui/visual/FlaiEditorTheme.kt` — **create**: single source of truth for accent colors (`JBColor` pairs for all 7 gate types), canvas background, grid, node chrome, and port colors; also holds `GRID_SIZE` constant. Exposes `accentFor(Gate)` and `accentForType(String)`.

- `src/main/kotlin/me/drew/flai/ui/visual/MinimapPanel.kt` — **create**: lightweight `JPanel` subclass, custom-painted, 160×100 px fixed size; reads model nodes/edges and `getViewTransform()` to render a scaled-down viewport indicator (FR-20/FR-21).

- `src/main/kotlin/me/drew/flai/ui/editor/FlaiIcons.kt` — **modify**: add 7 gate-type icon constants (mapped to `AllIcons` entries, or placeholder SVGs if SVG resources are added later).

- `src/main/kotlin/me/drew/flai/ui/visual/PipelineCanvas.kt` — **modify** (largest change):
  - Delete top-level `gateTypeColor(Gate)` function (moved to `FlaiEditorTheme`).
  - Add `GRID_SIZE`, dot-grid background rendering in `paintComponent`.
  - Rework `drawNode`: card background (`NODE_BG_DARK`), drop shadow, left accent border (4 px), gate icon left of label, rounded corners, hover shadow elevation.
  - Remove entry-gate star; add left-corner triangle badge for entry.
  - Add LLM-only top-right star as a hit-testable region; fire `onLlmStarClicked` on click.
  - Add `hoveredNodeSeq`, `nodeHoverAlpha`, `hoverTimer`; drive smooth hover alpha in `drawNode`.
  - Add `zoomLocked` field; guard `mouseWheelMoved` and `zoomIn()`/`zoomOut()` behind it.
  - Rework drag: hold `originalNodeX/Y`, accumulate `ghostDragOffsetX/Y` during drag, skip `model.moveNode()` until `mouseReleased`; paint ghost overlay in `paintComponent`.
  - Snap-to-grid: in `mouseReleased` for node drop, round final position to nearest `GRID_SIZE`.
  - `drawEdge`: brighten edges connected to selected or hovered node (FR-28).
  - `drawPort`: gray-blue input ports, green output ports, glow ring when parent selected/hovered.
  - Override `getToolTipText(MouseEvent)`: return port name for port hit-regions, `null` otherwise; register `ToolTipManager`.
  - `zoomIn()`, `zoomOut()` public methods (FR-22); both are no-ops when `zoomLocked == true`.
  - Add `onLlmStarClicked: ((VisualNode) -> Unit)` callback.
  - Add `onRepaint: (() -> Unit)` callback; invoke at end of `paintComponent`.
  - Note: zoom control buttons and `MinimapPanel` are NOT added as children of `PipelineCanvas`; they are added to the `JLayeredPane` overlay panel managed by `FlaiPipelineFileEditor` (see that file's entry below).

- `src/main/kotlin/me/drew/flai/ui/visual/GatePalettePanel.kt` — **modify**:
  - Delete local `gateTypeColor(String)` function (now uses `FlaiEditorTheme.accentForType`).
  - Replace `JButton` palette items with `GatePillPanel` inner class (`JPanel`, non-opaque, `paintComponent` draws rounded background in accent color); `FlaiIcons` gate icon on the left, text label on the right; `setToolTipText` for FR-18.
  - Add search `JTextField` at top (hidden when `isCollapsed`); filter `gateTypes` list in real time; show "No results" `JLabel` when filter is non-empty and matches nothing.
  - Add collapse toggle button; toggle `isCollapsed`; when collapsed, hide labels and search field, show icon-only pills.
  - `isCollapsed` setter persists to `PropertiesComponent.getInstance(project)` and fires `onCollapseToggled(isCollapsed)`.
  - Expose `var onCollapseToggled: ((Boolean) -> Unit) = {}` for the splitter proportion update.
  - Constructor: `class GatePalettePanel(private val project: Project, parentDisposable: Disposable)`. Register a `MessageBusConnection` for `LafManagerListener` on `project.messageBus`; pass `parentDisposable` to `Disposer.register` for cleanup.
  - `isEditable` setter: keep existing behavior; extend to also disable search field.

- `src/main/kotlin/me/drew/flai/ui/visual/NodePropertyPanel.kt` — **modify**:
  - Replace `buildMappingSection` implementation: remove `JTable` and `+/-` buttons; build a `JPanel(BoxLayout Y_AXIS)` of row panels; each row has two `JTextField` + a trash `JButton`; show empty-state `JLabel` with icon when list is empty; `+ Add` button appends a new row panel.
  - Add `scrollToLlmFieldGroup()`: scrolls `scrollPane` to the Y-offset of the LLM fields section and calls `requestFocusInWindow()` on the first LLM text field (FR-34).
  - Empty state for "no node selected": replace bare `JLabel` with a styled panel (icon + message).
  - Group fields into card panels with titled borders: "Basic Info", "Tool Settings" (only for applicable gate types), "Input Mapping", "Output Mapping".
  - `labeledRow`: increase row height to 32 px (FR-53).
  - Text fields: apply custom border that paints a blue ring on focus via `FocusBorderTextField` private inner class or `addFocusListener` + `repaint` approach.
  - Typography: card titles use `JBUI.Fonts.label(13f).asBold()`; field labels use `JBUI.Fonts.label(12f)`; secondary labels use `JBUI.Fonts.label(11f)` in `UIManager.getColor("Label.disabledForeground")`.
  - Preferred width increased; `minimumSize` widened (FR-44).

- `src/main/kotlin/me/drew/flai/ui/editor/FlaiPipelineFileEditor.kt` — **modify**:
  - `buildUI()`: rebuild toolbar as taller `JPanel` with styled primary (`applyBtn`, `runBtn`) and secondary (`autoLayoutBtn`, `fitBtn`) groups; add `JSeparator` at bottom of toolbar panel; add tooltips to all buttons.
  - `runBtn` label: "Run" with `FlaiIcons.GUTTER_RUN` icon.
  - Store `mainSplit` as a field (`private lateinit var mainSplit: OnePixelSplitter`). Initial proportion: `0.18f` (expanded) or `0.06f` if palette starts collapsed. Properties panel proportion stays at `0.75f` on `centerSplit` (unchanged relative to canvas+palette area) but the absolute width grows because the palette shrinks.
  - Pass `project` and `this` (as `parentDisposable`) to `GatePalettePanel` constructor.
  - Restore `palettePanel.isCollapsed` from `PropertiesComponent.getInstance(project)` using key `"flai.palette.collapsed.${project.locationHash}"` before `buildUI()` is called; default is `false` (expanded).
  - Set `palettePanel.onCollapseToggled = { collapsed -> mainSplit.proportion = if (collapsed) 0.06f else 0.18f }`.
  - Wire canvas overlay: wrap `canvas` in a `JLayeredPane` (`canvasLayer`); add canvas at `JLayeredPane.DEFAULT_LAYER`; create transparent overlay panel (null layout, `isOpaque = false`) at `JLayeredPane.PALETTE_LAYER`; add `MinimapPanel` and zoom-button panel to overlay with absolute bounds; add `ComponentListener` on `canvasLayer` to resize overlay and reposition overlay children when layer size changes.
  - Wire `canvas.onRepaint = { minimapPanel.refresh() }`.
  - Wire `canvas.onLlmStarClicked = { node -> if (canvas.getSelectedNode()?.nodeSeq != node.nodeSeq) { canvas.onNodeSelected(node) }; propertyPanel.scrollToLlmFieldGroup() }`.
  - `centerSplit.firstComponent = canvasLayer` (was `canvas`).

---

## Implementation Order

1. **Create `FlaiEditorTheme.kt`** — define all color constants and `accentFor`/`accentForType` functions. No tests needed at this step; purely data. Commit.

2. **Update `FlaiIcons.kt`** — add 7 gate-type icon constants. Commit.

3. **Remove duplicate color functions** from `PipelineCanvas.kt` (top-level `gateTypeColor`) and `GatePalettePanel.kt` (private `gateTypeColor`); replace call sites with `FlaiEditorTheme.accentFor` / `FlaiEditorTheme.accentForType`. Verify plugin compiles and canvas still renders. Commit.

4. **Refactor `NodePropertyPanel.buildMappingSection`** — replace `JTable` + `+/-` with per-row panel list, `+ Add` button, trash icons, and empty state. This is the riskiest structural change; isolate it. Manually test add/remove round-trip on all mapping-capable gate types (OutputGate, LlmGate, ToolGate). Commit.

5. **Node card visual rework in `PipelineCanvas.drawNode`** — card background, drop shadow, left accent border (4 px wide), gate icon, rounded corners. Also: remove entry-gate star from `drawNode`; add left-corner triangle badge for entry. Do not touch interaction logic. Commit.

6. **LLM star icon** — add hit-region for LLM star in `drawNode`; add `findLlmStarAt(mx, my)` helper; fire `onLlmStarClicked` in `mousePressed`. Update `FlaiPipelineFileEditor` to wire the callback. Commit.

7. **Hover states and selection glow** — add `hoveredNodeSeq`, `nodeHoverAlpha` map, `hoverTimer`; update `mouseMoved` listener; drive alpha in `drawNode` for shadow elevation and border brightening; add selection outer glow. Commit.

8. **Port visuals and tooltips** — update `drawPort` to use `FlaiEditorTheme` port colors; add glow ring; override `getToolTipText(MouseEvent)` + register with `ToolTipManager`. Commit.

9. **Ghost drag + snap-to-grid** — rework `mouseDragged`/`mouseReleased` for ghost drag logic; add `originalNodeX/Y`, `ghostDragOffsetX/Y`, `isGhostDragging` state; render ghost in `paintComponent`; snap on `mouseReleased`; handle Escape cancel via `keyPressed`. Commit.

10. **Dot-grid canvas background** — paint dot grid in `paintComponent` before edges/nodes. Commit.

11. **Zoom controls + zoom lock** — add `zoomLocked` field; guard `mouseWheelMoved`; add `zoomIn()`/`zoomOut()` methods; add overlay button panel (zoom in/out/reset/lock) on canvas. Commit.

12. **`MinimapPanel`** — create class; integrate into `FlaiPipelineFileEditor` as a bottom-left overlay; wire `refresh()` call. Commit.

13. **`GatePalettePanel` rework** — add `project: Project, parentDisposable: Disposable` constructor params; replace `JButton` items with `GatePillPanel` custom-painted panels; add search field + real-time filter + "No results" state; add collapse toggle; persist collapse state via `PropertiesComponent`; expose `onCollapseToggled` callback; register `LafManagerListener` via `MessageBusConnection` with `Disposer` cleanup. Update `FlaiPipelineFileEditor` to pass `project` and `this`; restore collapse state on open; wire `onCollapseToggled` to update `mainSplit.proportion`. Wrap `canvas` in `JLayeredPane` for overlay children; wire `canvas.onRepaint`. Commit.

14. **`NodePropertyPanel` typography and grouping** — wrap field groups in titled card panels; update row heights; add blue focus ring on text fields; update font sizes/weights. Commit.

15. **Toolbar rework in `FlaiPipelineFileEditor.buildUI()`** — increase height, style primary/secondary button groups, add separator, add tooltips, add run icon. Adjust splitter proportions. Commit.

16. **Edge active-state brightening** — update `drawEdge` to brighten edges connected to selected or hovered node. Commit.

---

## Test Strategy

**Unit (no IDE runtime required):**
- `FlaiEditorTheme` — verify `accentFor` returns distinct, non-null `Color` instances for all 7 gate types in both light and dark LAF contexts (mock `UIManager` or use `JBColor.isDark()`).
- `GatePalettePanel` filter logic — extract the filter predicate into a pure function `filterGateTypes(query: String, types: List<String>): List<String>` and unit-test it: empty query returns all, partial match, case-insensitive, no match returns empty.
- Mapping row model — if a `MappingRowModel` data class is extracted, unit-test add/remove/sync-to-map.
- Snap-to-grid math — extract `snapToGrid(value: Int, gridSize: Int): Int` as a top-level or companion function; unit-test rounding behavior at midpoints and edges.

**Integration (requires `runIde` or `MockApplication`):**
- `PipelineCanvas` drag-and-drop: simulate a drag event sequence (press → drag beyond threshold → release at non-grid position) and verify the node lands at a snapped position.
- Palette collapse persistence: set `isCollapsed = true`, call the save path, instantiate a new `GatePalettePanel` with the same project, verify it reads `isCollapsed == true`.
- `NodePropertyPanel` mapping round-trip: call `showGate()` with a gate with 2 mappings, simulate adding a row and removing a row, assert the model receives the correct map via the `onUpdate` callback.

**Manual / visual (run in sandbox IDE via `./gradlew runIde`):**
- All 7 gate types render with distinct accent colors in both Darcula and IntelliJ Light themes.
- Switching LAF while editor is open refreshes palette and canvas without restart.
- Minimap reflects viewport position when panning.
- Zoom lock: verify scroll-wheel and on-canvas buttons are inert; lock button unlocks.
- Ghost drag: drag node, see ghost; press Escape, node returns to origin.
- LLM star click: selects node and scrolls properties panel to LLM fields.
- Empty mapping state appears when all rows removed; `+ Add` brings back a row.
- Palette search: type "llm" → only LLM visible; clear → all 7 visible; type "xyz" → "No results".
- Palette collapse: collapse → icon-only; reopen project → same collapsed state.
- Tooltips: hover toolbar buttons, port handles, palette items — all show tooltips.

---

## Risks

**R1: Ghost drag + existing undo stack interaction.**  
Currently `model.moveNode` is not an undoable edit (no `UndoableEdit.NodeMoved` exists). Deferring the move until release does not change this — the move is still not undoable. Document this explicitly. If undo of node moves is a future requirement, a `NodeMoved` edit type would need to be added, but it is out of scope here.  
_Mitigation:_ No action needed for this release; the current behavior is unchanged.

**R2: Minimap performance on large pipelines.**  
`MinimapPanel.refresh()` will be called every time the canvas repaints (including during drag). If the pipeline has many nodes, minimap painting could add latency.  
_Mitigation:_ Minimap renders at 1/8 scale; only paint node rectangles (no edge curves) in the minimap. If latency is observed, throttle refresh to at most one per 50 ms via a timer.

**R3: `LafManagerListener` registration in `GatePalettePanel`.**  
The palette panel must unregister the listener when disposed to avoid leaks. `GatePalettePanel` is created inside `FlaiPipelineFileEditor` which implements `Disposable` and cancels its scope in `dispose()`.  
_Mitigation:_ Register the `LafManagerListener` connection as a `Disposable` on the palette's parent `Disposer` tree. Alternatively, `FlaiPipelineFileEditor.dispose()` calls a `dispose()` method on `GatePalettePanel` that disconnects the listener.

**R4: Hover alpha animation timer running when editor is not visible.**  
`hoverTimer` will fire and call `repaint()` whenever `hoveredNodeSeq != -1`, even if the editor tab is not visible.  
_Mitigation:_ Start `hoverTimer` only when `hoveredNodeSeq` changes to a valid value; stop it when `hoveredNodeSeq` returns to `-1` and all `nodeHoverAlpha` values have converged to 0.0. Check `isShowing` before `repaint()`.

**R5: Custom pill items in `GatePalettePanel` and `TransferHandler`.**  
The existing palette drag uses `JButton.transferHandler`. Switching to custom `JPanel` pills requires moving the `TransferHandler` and `MouseMotionAdapter` onto each pill's `JPanel`. Drag initiation from a `JPanel` requires calling `TransferHandler.exportAsDrag` in `mouseDragged` — same pattern as current code, just on a different component type.  
_Mitigation:_ The pill `JPanel` must be non-opaque or paint its own rounded background; default `JPanel` is rectangular. Use `setOpaque(false)` and override `paintComponent` in a `GatePillPanel` inner class.

**R6: Blue focus ring on `JTextField` inside `NodePropertyPanel`.**  
Standard `JTextField` focus appearance is LAF-controlled. Overriding it requires either a custom `Border` that checks `isFocusOwner()` or a `FocusListener` that triggers `repaint()` and a custom painting delegate. The LAF may override the border again.  
_Mitigation:_ Use `addFocusListener` that sets a custom compound border (line border in selection blue + empty insets) on focus gained, and restores the original border on focus lost. Store the original border before first focus event.

---

## Questions for Business Analyst

_None_
