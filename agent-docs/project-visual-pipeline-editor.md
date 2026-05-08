# Feature: Visual Node-and-Edge Pipeline Editor

## Slug
visual-pipeline-editor

## Summary
A "Visual" tab added to the IntelliJ IDEA editor for `.flai.yaml` files that renders the pipeline as an interactive node-and-edge canvas, similar to n8n. Users can inspect, rearrange, and edit pipelines by direct manipulation — dragging nodes, connecting edges, and configuring gate properties in a side panel — and then write changes back to the YAML via an explicit Apply button. The YAML file remains the single source of truth at all times. The feature eliminates the need to mentally parse raw YAML graph structure when navigating or modifying complex pipelines, and provides a live execution overlay so users can watch their pipeline run gate-by-gate without leaving the visual view.

## Goals
- Render any valid `.flai.yaml` pipeline as a node-and-edge graph inside a Visual tab in the file editor
- Support all 7 gate types at launch: `input`, `output`, `llm`, `logic`, `tool`, `read-file`, `write-file`
- Allow users to add gates from a palette, reposition nodes by dragging, add and delete edges, and delete gates
- Provide a per-gate property panel for editing all gate-specific fields (e.g. prompt template, endpoint URL, condition branches, path, outputKey)
- Persist node layout positions in an optional sidecar file so spatial arrangement is preserved across sessions without touching the YAML schema
- Write visual edits back to the YAML file only when the user explicitly clicks Apply
- Keep Visual and Text tabs in sync: switching from Text to Visual reflects current buffer state; Apply in Visual updates the Text buffer
- Highlight gate execution state (idle / running / succeeded / failed) as an overlay during pipeline runs triggered from within the Visual tab
- Block Apply and surface a validation error banner when required gate fields are missing; allow Apply with a warning for best-effort comment preservation

## Non-Goals
- Creating a new `.flai.yaml` pipeline file from the canvas — file creation stays in the standard IDE flow (New file dialog or right-click in Project tree)
- Guaranteed 100% preservation of YAML comments, key ordering, or custom formatting on Apply — best-effort top-level comment preservation is provided; a one-time dialog warns the user the first time Apply will normalize or reorder an existing file
- Multi-file or cross-pipeline visualization (edges that span files)
- Export to formats other than `.flai.yaml`
- Collaborative or multi-user editing
- Undo/redo history for visual edits beyond what IntelliJ's standard undo stack offers — undo/redo is treated as a Phase 2 capability; MVP does not guarantee fine-grained visual undo

## User-Facing Behavior

### Opening the Visual tab
1. User opens any `*.flai.yaml` file in the editor.
2. Two tabs appear at the top of the editor area: **Text** (the existing YAML editor) and **Visual**.
3. Clicking **Visual** parses the current YAML buffer and renders the pipeline canvas.
4. If the YAML is malformed or fails to parse, the Visual tab displays a read-only error banner with the parse error message; the canvas is blank and no editing is available until the Text tab is corrected.

### Canvas and palette
5. Each gate appears as a labeled node. Node shape or color distinguishes gate type. The entry gate is visually marked.
6. Directed edges connect nodes. Edge labels show `fromPort`/`toPort` only when they differ from the default `out`/`in`.
7. A gate palette (sidebar or toolbar) lists all 7 gate types. Dragging a gate type from the palette onto the canvas creates a new gate with a generated ID and default field values; the new node appears unconnected.
8. Users drag existing nodes to rearrange layout. Layout positions are saved to the sidecar file (see Layout persistence below) when Apply is clicked — they are not written to the YAML.

### Connecting and disconnecting edges
9. Users drag from a node's output port handle to another node's input port handle to create an edge.
10. For `logic` gates, each branch port is shown as a named output handle. The property panel lists branch rows, each with an editable port name. Users can rename a branch by editing its port name, add a new branch row using an **Add Branch** button, and delete an existing branch row using a delete icon on that row. When a branch with a connected edge is deleted, the edge is silently removed from the canvas alongside the branch — no confirmation dialog is shown.
11. Clicking an edge and pressing Delete (or using a context menu) removes the edge.

### Editing gate properties
12. Clicking a node opens a property panel (side drawer or inline panel). The panel exposes all fields for that gate type with labeled form controls. For `llm` gates, the `promptTemplate` field is a full-size, always-visible multi-line text area that can be expanded vertically by dragging its resize handle — it is never collapsed to a single line.
13a. For `llm` gates, the `endpointConfig.credentialId` field is a plain text input that holds only the credential identifier (the key name used to look up the secret). Entering or updating the credential secret itself is not available from the Visual tab; users manage secrets through IntelliJ's standard credential store settings.
13b. For `tool` gates, the `toolName` field is a dropdown populated with the tools currently registered in the IDE at the time the property panel opens (e.g. `PsiSymbolSearchTool`, `FileReadTool`, `RunCommandTool`). Free-text entry is not permitted; the dropdown prevents referencing unregistered tools that would fail at runtime.
14. The property panel also exposes the gate's ID. Renaming a gate ID updates all connected edges automatically to reference the new ID — existing edges are not broken.
15. Changes in the property panel are reflected immediately on the canvas (e.g. label updates) but are not written to the YAML until Apply.

### Editing pipeline metadata
16. Clicking the canvas background (or a dedicated pipeline header strip at the top of the canvas) opens a pipeline-level property panel for the top-level `id`, `name`, and `description` fields.
17. The entry gate is changed by right-clicking any node and choosing **Set as Entry**. The canvas updates the entry marker immediately.

### Deleting gates
18. Selecting a node and pressing Delete (or using a context menu) removes the gate and all connected edges from the canvas.

### Apply
19. An **Apply** button is visible in the Visual tab toolbar at all times.
20. Before writing, the editor validates that all required fields are present on every gate. If validation fails, Apply is blocked and an error banner lists the affected gates and missing fields.
21. On the first Apply for any file that contains top-level YAML comments or non-default key ordering, a one-time dialog informs the user that Apply will normalize the file (comments inside gates may be lost) and offers Cancel to abort. Accepting is remembered per-file and the dialog does not appear again.
22. On successful Apply, the YAML buffer is updated with the canonical representation of the current visual state. The Text tab reflects this update immediately. The file is marked modified in IntelliJ's editor (unsaved indicator) but not yet saved to disk — the user saves via the standard Ctrl/Cmd+S or auto-save.

### Switching between tabs
23. Switching from Visual to Text always shows the current buffer state (the last Applied state, or the original file state if Apply has never been clicked). Unapplied visual edits are not written to the Text tab or the buffer.
24. Switching from Text to Visual re-parses the current buffer and re-renders the canvas. If the Visual tab has pending unapplied edits when the user navigates away to Text and back, a confirmation prompt asks whether to discard those edits and reload from the buffer.

### Live execution overlay
25. The Visual tab toolbar includes a **Run** button (equivalent to the gutter run icon). Clicking Run prompts for any required `input` gate fields, then starts the pipeline.
26. While running, each node shows a live state badge: spinning indicator for the currently executing gate, green check for succeeded, red X for failed.
27. A **Cancel** button in the toolbar replaces Run during execution and stops the pipeline.
28. When execution completes, badges remain visible until the user manually clears them or starts a new run.

### Layout persistence
29. Node positions are stored in an optional sidecar file co-located with the YAML, named `<pipeline-filename>.layout.json` (e.g. `my-pipeline.flai.yaml.layout.json`), in the same `.flai/` directory.
30. If the sidecar file is absent or missing an entry for a gate, that gate is placed using automatic layout.
31. The sidecar file is written when Apply is clicked. It is not written on drag-only sessions where Apply is never clicked.
32. The sidecar file is not required for pipeline execution and is safe to delete; the pipeline runs from the YAML alone.

## Open Decisions
- Whether the sidecar `.layout.json` file should be added to `.gitignore` by default, or committed alongside the YAML. A case can be made either way: layout is a presentation concern (ignore it) vs. a shared team asset (commit it). Deferred to BA/team convention.
- Multi-select of nodes (for bulk move or bulk delete) is deferred to Phase 2. MVP supports single-node selection only.

## Questions
_None_

