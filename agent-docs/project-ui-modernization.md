# Feature: Visual Pipeline Editor UI Modernization

## Slug
ui-modernization

## Summary
A visual refresh of the node-based pipeline editor introduced in the `visual-pipeline-editor` feature. Every region of the editor — toolbar, gate palette, canvas, nodes, and properties panel — adopts a soft dark aesthetic with clearer visual hierarchy, type-coded nodes, and richer interaction feedback (hover states, selection glow, snap-to-grid, ghost drag preview). No business logic, pipeline execution, YAML schema, or data structures change. The goal is to reduce visual noise, make gate type identifiable at a glance, and give the canvas a focused workspace feel that fits naturally inside IntelliJ IDEA.

## Goals
- Replace the flat gray visual style with a coordinated soft dark palette across all editor regions
- Make each of the 7 gate types immediately identifiable by a consistent accent color (green for input, orange for output, blue for llm, yellow for logic, purple for tool, amber for read-file, pink/red for write-file)
- Add a search field to the gate palette so users can filter gate types without scrolling
- Give the canvas a workspace feel: dot or line grid background, minimap, and explicit zoom controls (zoom in, zoom out, reset, lock)
- Provide snappier, smoother hover and selection feedback on nodes, edges, and ports so the editor feels responsive
- Reduce visual noise in the properties panel by grouping fields into labeled cards with empty states for mapping tables
- Add tooltips to toolbar buttons, port handles, and palette items to improve discoverability

## Non-Goals
- Changes to YAML schema, gate IDs, mapping API, graph data structure, or pipeline execution logic
- New gate types, new IDE tools, or new pipeline capabilities
- Changes to Apply / Run / tab-switching semantics defined in `project-visual-pipeline-editor.md`
- A user-customizable theming engine — this is a single coordinated visual refresh, not a theme switcher
- A user-facing theme switcher or per-user palette customization (the dark and light variants are tied to the IDE theme, not to a user preference inside the plugin)

## User-Facing Behavior

### Top Toolbar
The toolbar is taller and more prominent than before. "Apply" and "Run" are the primary actions: "Run" displays a play icon. Secondary actions — auto-layout, fit-to-view — sit alongside them. Settings and overflow icons appear on the right end. A separator line divides the toolbar from the canvas. All toolbar buttons show tooltips on hover.

### Left Gate Palette
A search field labeled "Search gates..." appears at the top of the palette. Typing filters the visible gate types in real time; clearing the field restores all types. Each gate type is displayed as a colored pill or card with an icon and label. The palette has a collapse button; when collapsed it shows only icons, freeing horizontal space for the canvas. Hovering a palette item highlights it. All palette items show tooltips.

When the palette is collapsed (icon-only mode), the search field is hidden. Activating search — by clicking anywhere in the icon column or pressing a search shortcut — automatically expands the palette and focuses the search field. The palette does not offer a search affordance while remaining in collapsed state.

### Canvas / Workspace
The canvas background is dark with a subtle dot or line grid at low contrast. A minimap appears in the bottom-left corner showing the full pipeline extent and the current viewport. Zoom controls (zoom in, zoom out, reset zoom, lock zoom) are visible on the canvas edge. When zoom is locked, all zoom input is disabled: scroll-wheel, pinch gestures, and the zoom-in / zoom-out / reset buttons on the canvas edge all become inert. The lock button itself remains active so the user can unlock. Nodes snap to grid when dragged; a ghost preview of the node follows the cursor during a drag before it is dropped.

Edges are rendered as Bezier curves. Active edges (connected to a selected node or hovered) appear brighter than idle edges. Edge behavior (create, delete, port labeling) is unchanged from `project-visual-pipeline-editor.md`.

### Nodes
Each node has a card-style appearance with rounded corners, a soft drop shadow, and a left border or background tint in the accent color for its gate type. An icon appears inside the node to the left of the label, using the same accent color. The entry gate carries a visible marker (unchanged behavior, refreshed visual).

LLM gate nodes carry a star icon in their top-right corner. This icon opens a quick-config popover (or focuses the properties panel on the LLM-specific fields) without requiring the user to locate the right panel first. No other gate type carries this icon. The entry gate marker and the LLM star icon are the only "special" node decorations in this release.

Selected state: the node shows a bright blue outline and an outer glow. Hover state: the shadow lifts slightly and the border brightens. These transitions are smooth and quick so the editor feels immediate.

Port handles are circular. Input ports are gray-blue; output ports are green. A port shows a glow effect when its node is selected or hovered. Ports show tooltips on hover.

### Right Properties Panel
The panel is wider than before. It is not collapsible in this release; it is always visible when a node is selected. When nothing is selected the panel shows an empty state ("Select a node to view its properties"). Responsive narrowing of the tool window simply lets the panel reflow its content — no collapse control, no icon strip. Fields are grouped into labeled cards: Basic Info, Tool Settings (when applicable), Input Mapping, and Output Mapping. Each card has a title and contains its fields with consistent spacing.

Input and output mapping sections show an empty state — an icon plus a short message ("No mappings added") — when the list is empty, rather than an empty table. Adding a mapping uses an "+ Add" button; removing one uses a trash icon on the row. The old +/- button pair is replaced.

Text fields have a taller click target and show a blue focus ring when active. Typography is consistent with the JetBrains / IntelliJ system font: panel titles are larger and semibold; labels and inputs are smaller and regular; secondary labels appear in a muted color.

## Open Decisions
- **Dark vs. light theme compatibility — RESOLVED.** The dark palette applies only when IntelliJ is running a dark IDE theme. Users on a Light theme receive a separate light-mode variant of the editor (light canvas, appropriately tinted node accents) so the editor does not conflict with IDE chrome. Color and contrast values in the BA spec must therefore be defined in two sets: one for dark-theme environments and one for light-theme environments.
- **"Special node" star/action icon — RESOLVED.** Only LLM gate nodes carry this icon (see User-Facing Behavior → Nodes). The icon focuses the LLM-specific fields in the properties panel. All other gate types, including the entry gate, do not carry this decoration.
- **Palette collapse state persistence — RESOLVED.** The expanded/collapsed state of the left gate palette is saved and restored per-project across sessions. The palette opens in whatever state the user left it.
- **Right panel collapsibility — RESOLVED.** The right properties panel is not collapsible in this release. It is always visible; see User-Facing Behavior → Right Properties Panel.

## Questions
_None_
