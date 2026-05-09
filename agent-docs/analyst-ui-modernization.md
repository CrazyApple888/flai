# Functional Spec: Visual Pipeline Editor UI Modernization

## Functional Requirements

### Top Toolbar

- FR-1: The toolbar renders taller and more visually prominent than the current implementation.
- FR-2: "Apply" and "Run" are presented as the primary action buttons. "Run" displays a play icon alongside its label.
- FR-3: Secondary actions (auto-layout, fit-to-view) appear in the toolbar alongside the primary actions.
- FR-4: Settings and overflow controls appear at the right end of the toolbar.
- FR-5: A visible separator line divides the toolbar from the canvas below it.
- FR-6: Every toolbar button displays a tooltip when hovered.

### Left Gate Palette

- FR-7: A text input field labeled "Search gates..." appears at the top of the palette when the palette is expanded.
- FR-8: Typing in the search field filters the visible gate type items in real time; only gate types whose names match the input are shown.
- FR-9: Clearing the search field (emptying the input) restores all gate types in the palette.
- FR-10: When the search field is non-empty and no gate types match, the palette shows an explicit empty state (e.g., a "No results" message) rather than a blank area.
- FR-11: Each gate type is represented as a colored pill or card displaying an icon and a text label.
- FR-12: Each gate type's pill/card uses the designated accent color for that type. The editor detects the active IDE theme and applies the appropriate color variant:

  **Dark-theme variant** (active when IntelliJ is running a dark LAF, e.g., Darcula):
  - Input gate: saturated mid-green on dark canvas
  - Output gate: saturated amber-orange on dark canvas
  - LLM gate: saturated mid-blue on dark canvas
  - Logic gate: saturated yellow on dark canvas
  - Tool gate: saturated purple on dark canvas
  - Read-file gate: saturated amber on dark canvas
  - Write-file gate: saturated pink/red on dark canvas

  **Light-theme variant** (active when IntelliJ is running a light LAF):
  - Input gate: deeper green on light canvas
  - Output gate: deeper amber-orange on light canvas
  - LLM gate: deeper blue on light canvas
  - Logic gate: deeper yellow-ochre on light canvas
  - Tool gate: deeper purple on light canvas
  - Read-file gate: deeper amber on light canvas
  - Write-file gate: deeper pink/red on light canvas

  The same color variants govern accent colors everywhere in the editor (node borders, node icons, port tints) — FR-12 is the single source of truth for both palettes.

- FR-13: The palette has a collapse/expand toggle button.
- FR-14: When the palette is collapsed, it shows only gate-type icons (no text labels, no search field), freeing horizontal space for the canvas.
- FR-15: The collapsed/expanded state of the gate palette is persisted per project and is restored automatically when the project is next opened.
- FR-16: When the palette is in collapsed (icon-only) mode, there is no search affordance visible. Activating search — by clicking anywhere in the icon column or by pressing the designated search shortcut — automatically expands the palette and places focus in the search field.
- FR-17: Hovering a palette item produces a visible highlight on that item.
- FR-18: Every palette item displays a tooltip on hover.

### Canvas / Workspace

- FR-19: The canvas background uses a palette matched to the active IDE theme: dark with a subtle dot or line grid at low contrast (dark variant); light with a subtle dot or line grid at low contrast (light variant).
- FR-20: A minimap is displayed in the bottom-left corner of the canvas, showing the full pipeline extent and the current viewport position.
- FR-21: When the pipeline is empty, the minimap shows an appropriate empty state (no content represented).
- FR-22: Zoom controls are visible on the canvas edge: zoom in, zoom out, reset zoom, and lock zoom.
- FR-23: When zoom is locked:
  - Scroll-wheel zoom is disabled.
  - Pinch-to-zoom gestures are disabled.
  - The on-canvas zoom-in button is disabled (inert).
  - The on-canvas zoom-out button is disabled (inert).
  - The on-canvas reset-zoom button is disabled (inert).
  - The lock button itself remains active so the user can unlock.
- FR-24: When a node is dragged, a ghost (semi-transparent) preview of the node follows the cursor until the node is dropped.
- FR-25: When a drag is cancelled (e.g., Escape key or drop outside the canvas), the ghost preview disappears and the node returns to its original position.
- FR-26: Nodes snap to grid during drag; the drop position is the nearest grid point.
- FR-27: Edges are rendered as Bezier curves.
- FR-28: Edges connected to a selected or hovered node appear visually brighter than idle edges.

### Nodes

- FR-29: Each node has a card-style appearance with rounded corners and a visible drop shadow.
- FR-30: Each node displays a left border or background tint in the accent color corresponding to its gate type (using the same color assignments as FR-12).
- FR-31: Each node displays an icon to the left of its label using the same accent color as its gate type.
- FR-32: The entry gate node carries a visible entry marker (visual refresh of existing behavior; the marker's function is unchanged).
- FR-33: LLM gate nodes display a star/action icon in their top-right corner. No other gate type carries this icon.
- FR-34: Activating the LLM star/action icon (clicking it) selects the node if it is not already selected, then focuses the LLM-specific field group in the properties panel (scrolls to and places keyboard focus on the first LLM field).
- FR-35: A selected node displays a bright blue outline with an outer glow effect.
- FR-36: A hovered node (not selected) displays an elevated shadow and a brightened border compared to its idle state.
- FR-37: Transitions between idle, hover, and selected visual states are smooth and perceptibly quick (not instant flicker, not sluggish).
- FR-38: Port handles are circular.
- FR-39: Input port handles are rendered in gray-blue.
- FR-40: Output port handles are rendered in green.
- FR-41: When a node is selected or hovered, its port handles display a glow effect.
- FR-42: Each port handle displays a tooltip on hover.

### Right Properties Panel

- FR-43: The properties panel is always visible when the editor is open. It is not collapsible in this release. Responsive narrowing of the tool window causes the panel to reflow its content; no collapse control or icon strip is introduced.
- FR-44: The properties panel is wider than the current implementation.
- FR-45: When no node is selected, the properties panel shows an appropriate empty state (e.g., "Select a node to view properties") rather than blank space or stale content.
- FR-46: When a node is selected, its fields are grouped into labeled cards: "Basic Info", "Tool Settings" (shown only for gate types that have tool settings), "Input Mapping", and "Output Mapping".
- FR-47: Each card has a visible title and consistent internal spacing between fields.
- FR-48: When the Input Mapping list is empty, the card shows an empty state: an icon and a short message (e.g., "No mappings added"), not an empty table.
- FR-49: When the Output Mapping list is empty, the card shows the same empty state pattern as FR-48.
- FR-50: Each mapping section provides an "+ Add" button to add a new mapping row.
- FR-51: Each existing mapping row provides a trash icon button to remove that row individually.
- FR-52: The old +/- button pair for mapping management is removed.
- FR-53: Text input fields have a taller click target than the current implementation.
- FR-54: An active (focused) text input field displays a visible blue focus ring.
- FR-55: Panel section titles are rendered larger and semibold relative to field labels and input text.
- FR-56: Field labels and input text are rendered in a regular weight at a smaller size than panel titles.
- FR-57: Secondary / helper labels are rendered in a muted (lower-contrast) color distinct from primary label text.
- FR-58: All panel typography uses the IntelliJ system font (the JBUI default), consistent with the surrounding IDE chrome.

---

## User Stories

**US-1: Developer scanning a pipeline at a glance**
As a developer viewing a pipeline in the editor, I want each node to be immediately identifiable by gate type at a glance so that I can understand the pipeline structure without reading every label.

**US-2: Developer searching for a gate type to add**
As a developer composing a pipeline, I want to type in the gate palette search box to narrow the list so that I can find the gate type I want without scrolling through all options.

**US-3: Developer working on a large pipeline**
As a developer editing a large pipeline, I want a minimap and zoom controls visible on the canvas so that I can navigate the workspace and understand pipeline extent without losing my place.

**US-4: Developer placing nodes precisely**
As a developer positioning nodes, I want nodes to snap to a grid and show a ghost preview while dragging so that I can place nodes accurately and see where they will land before releasing.

**US-5: Developer reading node connections**
As a developer tracing data flow, I want edges near selected or hovered nodes to appear brighter than idle edges so that I can follow active connections without visual noise from unrelated edges.

**US-6: Developer configuring a gate**
As a developer configuring a selected gate in the properties panel, I want fields grouped into labeled cards with a clear empty state for empty mapping lists so that I can scan what is configured and what is not without reading through empty table rows.

**US-7: Developer managing input/output mappings**
As a developer editing mappings, I want an "+ Add" button to add rows and a per-row trash icon to remove them so that I can manage mappings with clear, discoverable affordances.

**US-8: Developer discovering toolbar and palette actions**
As a developer unfamiliar with toolbar buttons or palette items, I want tooltips to appear on hover so that I can learn what each control does without consulting documentation.

**US-9: Developer using a narrow tool window**
As a developer working with limited horizontal screen space, I want to collapse the gate palette to icon-only mode so that the canvas has more room.

**US-10: Developer quickly configuring an LLM gate**
As a developer who wants to adjust the LLM-specific configuration of a node, I want a star icon on the LLM node to immediately bring me to the LLM fields in the properties panel so that I do not have to scroll or locate the right field group manually.

**US-11: Developer resuming work on a project**
As a developer reopening a project, I want the palette to restore to the same expanded or collapsed state I left it in so that my workspace layout is preserved between sessions.

---

## Acceptance Criteria

**AC for US-1 / FR-29–FR-32, FR-12:**
- Given a pipeline with gates of multiple types, each node in the canvas displays a distinct accent color (border or tint) matching its gate type, from the seven assigned colors.
- An icon is visible inside each node, using the same accent color.
- The entry gate node shows a visible entry marker.
- With IntelliJ in a dark LAF, nodes display the dark-variant palette. With IntelliJ in a light LAF, nodes display the light-variant palette.

**AC for US-2 / FR-7–FR-10:**
- Given the palette is expanded, a text field labeled "Search gates..." is visible at the top.
- Typing "llm" causes only the LLM gate type item to be visible; all others are hidden.
- Clearing the input restores all seven gate type items.
- Typing a string that matches no gate type causes all items to be hidden and a "No results" message to appear in the palette area.

**AC for US-3 / FR-20–FR-23:**
- A minimap is visible in the bottom-left corner of the canvas at all times while the editor is open.
- The minimap reflects the current viewport position when the user pans.
- Zoom in, zoom out, reset zoom, and lock zoom controls are visible on the canvas edge.
- When zoom is locked, scroll-wheel zoom gestures produce no zoom change on the canvas.
- When zoom is locked, pinch-to-zoom gestures produce no zoom change on the canvas.
- When zoom is locked, the on-canvas zoom-in, zoom-out, and reset-zoom buttons are inert (clicking them produces no zoom change).
- When zoom is locked, the lock button itself remains clickable and unlocks zoom when activated.

**AC for US-4 / FR-24–FR-26:**
- When a node is picked up and dragged, a semi-transparent ghost preview appears and follows the cursor.
- When the node is released, it drops to the nearest grid snap point.
- When Escape is pressed during a drag, the ghost disappears and the node returns to its pre-drag position.

**AC for US-5 / FR-27–FR-28:**
- All edges render as Bezier curves (no straight-line segments).
- When a node is selected, all edges connected to it are visually brighter than edges not connected to any selected node.
- When a node is hovered, connected edges brighten; they return to idle brightness when the hover ends.

**AC for US-6 / FR-44–FR-49:**
- When no node is selected, the properties panel shows an empty state message rather than stale content.
- The properties panel has no collapse toggle; it is always visible regardless of tool window width.
- When a node with no tool settings (e.g., an input gate) is selected, the "Tool Settings" card is not shown.
- When a node with tool settings is selected, the "Tool Settings" card is shown.
- When input mapping is empty, the Input Mapping card shows an icon and "No mappings added" text, not a table with zero rows.
- Same behavior applies to Output Mapping.

**AC for US-7 / FR-50–FR-52:**
- An "+ Add" button is present in each mapping card (Input Mapping, Output Mapping).
- Clicking "+ Add" appends a new editable mapping row.
- Each existing mapping row has a trash icon; clicking it removes only that row.
- No +/- button pair is present.

**AC for US-8 / FR-6, FR-18, FR-42:**
- Hovering any toolbar button for a standard hover-delay interval causes a tooltip to appear.
- Hovering any palette item causes a tooltip to appear.
- Hovering any port handle causes a tooltip to appear.

**AC for US-9 / FR-13–FR-14:**
- The palette has a collapse toggle button.
- When collapsed, the palette shows only icons (no text labels, no search field) for each gate type.
- When expanded, the palette shows full pill/card items with icons and labels, and the search field is visible.

**AC for US-10 / FR-33–FR-34:**
- An LLM gate node displays a star icon in its top-right corner.
- No other gate type node displays this star icon.
- Clicking the star icon on an unselected LLM node selects that node and then scrolls the properties panel to and places keyboard focus on the first field in the LLM-specific field group.
- Clicking the star icon on an already-selected LLM node scrolls and focuses the LLM field group without deselecting and reselecting.

**AC for US-11 / FR-15:**
- When a user collapses the palette, closes and reopens the project, the palette is displayed in collapsed (icon-only) mode.
- When a user expands the palette, closes and reopens the project, the palette is displayed in expanded mode.

**AC for FR-35–FR-37 (node interaction states):**
- A selected node displays a bright blue outline with an outer glow distinguishable from its hover and idle states.
- A hovered (unselected) node displays an elevated shadow and brightened border compared to idle.
- The visual transition between states is not an instant cut; it is perceptibly smooth without being sluggish.

**AC for FR-38–FR-41 (port handles):**
- Input port handles are circular and rendered in gray-blue.
- Output port handles are circular and rendered in green.
- When the parent node is hovered or selected, all of its port handles display a glow effect.

**AC for FR-53–FR-57 (properties panel typography):**
- Active text inputs show a blue focus ring.
- Panel card titles are visually larger and semibold relative to field labels.
- Helper/secondary labels appear in a muted color distinct from primary label text.

---

## Edge Cases & Error Scenarios

- **Palette search while collapsed:** The search field is hidden and no search affordance is visible when the palette is in collapsed (icon-only) mode. Clicking anywhere in the icon column or pressing the designated search shortcut automatically expands the palette and places focus in the search field. The palette does not allow search input while remaining collapsed.
- **Palette search, no results:** When no gate types match the search input, the palette area shows an explicit "No results" (or equivalent) message; it does not show a blank region.
- **Palette collapse state, new project:** A project that has never had the palette state set opens with the palette in a defined default state (expanded).
- **Snap-to-grid for already-placed nodes:** Only nodes being actively dragged snap to grid. Existing nodes do not reflow to grid positions automatically when snap is enabled.
- **Ghost drag preview, drop outside canvas:** If the user releases a drag outside the canvas bounds, the drag is cancelled, the ghost disappears, and the node returns to its original position.
- **Ghost drag preview, Escape during drag:** Pressing Escape cancels the drag; ghost disappears and node returns to pre-drag position.
- **Zoom lock scope:** When zoom is locked, scroll-wheel zoom, pinch-to-zoom, and the on-canvas zoom-in, zoom-out, and reset-zoom buttons are all disabled. Only the lock toggle button itself remains active.
- **Zoom lock, keyboard zoom shortcuts:** If the IDE or canvas supports keyboard-based zoom shortcuts, those shortcuts must also be blocked when zoom is locked.
- **Minimap, empty pipeline:** When no gates are placed on the canvas, the minimap shows an appropriate empty state (not a crash or blank with no indicator).
- **Minimap, large pipeline:** When the pipeline extent greatly exceeds the viewport, the minimap continues to render the full extent at reduced scale without overflow or clipping.
- **Properties panel, no node selected:** The panel shows an empty state message. No stale content from a previously selected node remains visible.
- **Properties panel, tool window resized:** When the tool window is narrowed or widened, the properties panel reflows its content. No collapse control appears.
- **Mapping card, row added then removed:** If a user adds a mapping row and then removes it, and the list becomes empty again, the empty state ("No mappings added") reappears.
- **"Tool Settings" card visibility:** The card appears only for gate types that have tool settings. For gate types without tool settings, the card is entirely absent (not shown as an empty card).
- **Long gate type label in collapsed palette:** When the palette is in icon-only mode, gate type labels are not shown; icons must fit within the collapsed palette width without clipping or overflow.
- **Accent color applied to all nodes of a type:** All nodes of the same gate type use the same accent color, regardless of node name or position on the canvas.
- **Theme switch at runtime:** If the user switches the IDE LAF while the editor is open, the editor must update to the appropriate color variant without requiring a restart. (If live runtime switching is not implementable, the behavior must be defined: either update on restart or update on file close/reopen.)
- **LLM star icon, properties panel not yet loaded:** If the properties panel has not yet rendered the selected node's fields, clicking the star icon must still trigger selection and scroll to the LLM field group once the panel finishes loading.
- **Entry gate marker, multiple entry gates:** This is not a new behavior constraint; inherited rules from `project-visual-pipeline-editor.md` apply. The marker is a visual refresh only.

---

## Non-Functional Requirements

- NFR-1: Hover state and selection state transitions on nodes must be perceptibly smooth (not an instant flicker) and quick (not a noticeable lag on a modern development machine).
- NFR-2: Real-time palette search filtering must produce visible results without perceptible lag as the user types at normal speed.
- NFR-3: The ghost drag preview must track the cursor position without perceptible lag during a drag operation.
- NFR-4: Both the dark-theme and light-theme color palettes must maintain sufficient contrast between gate accent colors and their respective canvas backgrounds so that node type differentiation is visible without reliance on labels alone. (Specific contrast ratios are a design decision, not specified here.)
- NFR-5: No change to pipeline execution performance, YAML parsing, or domain model behavior is introduced by this feature.

---

## Out of Scope

- Changes to YAML schema, gate IDs, output mapping API, or graph data structures.
- Changes to pipeline execution logic or `GateExecutor` implementations.
- New gate types or new IDE tools.
- Changes to Apply / Run / tab-switching semantics as defined in `project-visual-pipeline-editor.md`.
- A user-customizable theming engine or theme switcher — the dark and light variants are tied to the IDE LAF, not to a user preference inside the plugin.
- Keyboard / accessibility behavior beyond what IntelliJ's standard Swing components provide by default (tooltip keyboard triggering, focus traversal, etc.) — no explicit a11y requirements are stated in the PM doc.
- Edge creation, deletion, and port labeling behavior — inherited unchanged from `project-visual-pipeline-editor.md`.
- Any other behavior inherited from `project-visual-pipeline-editor.md` that this document does not explicitly modify.

---

## Questions for PM

_None_
