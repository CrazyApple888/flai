# Functional Spec: Visual Node-and-Edge Pipeline Editor

## Functional Requirements

### Tab & Rendering

- FR-1: The system must add a **Visual** tab alongside the existing **Text** tab whenever a `*.flai.yaml` file is opened in the editor.
- FR-2: Clicking the Visual tab must parse the current YAML buffer and render the pipeline as a directed node-and-edge canvas.
- FR-3: Each gate must appear as a distinctly labeled node; node shape or color must differ by gate type so all 7 types are visually distinguishable.
- FR-4: The entry gate (the gate referenced by the pipeline's `entry` field) must be visually marked as the entry node.
- FR-5: Directed edges must be drawn between nodes according to the `edges` list. An edge label showing `fromPort`/`toPort` must be displayed only when those values differ from the defaults (`out`/`in`).
- FR-6: If the YAML buffer fails to parse or is structurally invalid, the Visual tab must display a read-only error banner containing the parse error message and present a blank, non-interactive canvas. Editing must not be available until the buffer is corrected in the Text tab.

### Gate Palette

- FR-7: A gate palette must list all 7 gate types: `input`, `output`, `llm`, `logic`, `tool`, `read-file`, `write-file`.
- FR-8: Dragging a gate type from the palette onto the canvas must create a new gate with a generated, unique ID and default values for all fields of that gate type; the new gate must appear on the canvas unconnected to any other gate.
- FR-9: Generated gate IDs must not collide with any existing gate ID in the current pipeline.

### Node Layout

- FR-10: Users must be able to drag existing nodes to reposition them on the canvas. Repositioned nodes must retain their new positions until the session ends, Apply is clicked, or the canvas is reloaded.
- FR-11: Node layout positions must be stored in the sidecar file (see FR-29) only when the user clicks Apply; drag-only sessions where Apply is never clicked must not write the sidecar file.
- FR-12: If a gate has no entry in the sidecar file, the system must apply automatic layout to position that gate.

### Edges — Creating & Deleting

- FR-13: Users must be able to create an edge by dragging from a node's output port handle to another node's input port handle.
- FR-14: `InputGate` has no input port; the system must not allow any edge to target an `InputGate`.
- FR-15: For `LogicGate` nodes, each branch defined in the gate's `branches` list must be shown as a named output port handle, plus one handle for `defaultPort`. Dragging from a branch handle must set the edge's `fromPort` to that branch's port name.
- FR-16: Users must be able to delete an edge by selecting it and pressing Delete, or via a context-menu action on the edge. The corresponding entry is removed from the pipeline's `edges` list in the visual state.
- FR-17: The system must not permit two edges with identical (`from`, `fromPort`, `to`, `toPort`) tuples on the same canvas.

### Gate Property Panel

- FR-18: Clicking a node must open a property panel exposing all fields for that gate type. The fields per gate type are:
  - **InputGate**: gate ID; `inputSchema` (list of fields, each with `name`, `type`, `required`, `default`); list rows must be addable and removable.
  - **OutputGate**: gate ID; `outputMapping` (map of output-key → context-variable); key/value rows must be addable and removable.
  - **LlmGate**: gate ID; `promptTemplate` (full-size, always-visible multi-line text area with a vertical resize handle — never collapsed to a single line); `skills` (ordered list of file paths, rows addable/removable); `inputMapping` (key/value rows, addable/removable); `outputMapping` (key/value rows, addable/removable); `endpointConfig.url`; `endpointConfig.credentialId` (plain text input for the credential identifier only — entering or updating the credential secret itself is not available from the Visual tab; users manage secrets via IntelliJ's standard credential store settings); `endpointConfig.model`; `endpointConfig.params` (key/value rows, addable/removable).
  - **LogicGate**: gate ID; `branches` list (each branch row exposes: `port` name, condition type selector, condition fields per type — see FR-19); `defaultPort` (text field); branches are added via an **Add Branch** button and removed via a per-row delete icon. When a branch with a connected edge is deleted, that edge is silently removed from the canvas with no confirmation dialog.
  - **ToolGate**: gate ID; `toolName` (dropdown populated with tools registered in the IDE at the time the property panel opens — free-text entry is not permitted); `inputMapping` (key/value rows); `outputMapping` (key/value rows).
  - **ReadFileGate**: gate ID; `path` (text field); `outputKey` (text field).
  - **WriteFileGate**: gate ID; `path` (text field); `contentKey` (text field); `mode` (dropdown: `overwrite`, `append`, `fail-if-exists`).
- FR-19: For `LogicGate` branches, the condition type selector must offer three options: `Comparison`, `SwitchCase`, `Always`. Selecting `Comparison` must expose `variable`, `op` (dropdown of `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `CONTAINS`, `STARTS_WITH`), and `value` fields. Selecting `SwitchCase` must expose `variable` and a multi-value list. Selecting `Always` must expose no additional fields.
- FR-20: Renaming a gate's ID in the property panel must update all edges that reference the old ID (in `from` or `to` fields) to reference the new ID automatically; no existing edges must be broken by an ID rename.
- FR-21: A gate ID rename must be rejected if the new ID is empty or if the new ID already exists on another gate in the pipeline.
- FR-22: Changes made in the property panel must be reflected immediately on the canvas (e.g., updated node label) but must not be written to the YAML buffer until Apply is clicked.

### Pipeline Metadata

- FR-23: Clicking the canvas background or a dedicated pipeline header strip must open a pipeline-level property panel exposing the top-level `id`, `name`, and `description` fields for editing.
- FR-24: Right-clicking any node and choosing **Set as Entry** must update the pipeline's `entry` field to that gate's ID and move the entry visual marker to that node.

### Deleting Gates

- FR-25: Selecting a node and pressing Delete, or using a context-menu action, must remove the gate from the canvas and remove all edges connected to that gate.

### Apply

- FR-26: An **Apply** button must be permanently visible in the Visual tab toolbar, regardless of whether there are pending edits.
- FR-27: Before writing, the system must validate that all required fields are present on every gate and on the pipeline metadata. If validation fails, Apply must be blocked and an error banner must list each affected gate (by ID) and the specific missing fields. No partial write occurs. Required fields per gate type:
  - **InputGate**: gate ID is required.
  - **OutputGate**: gate ID is required.
  - **LlmGate**: gate ID, `promptTemplate`, `endpointConfig.url`, `endpointConfig.credentialId`, `endpointConfig.model` are required.
  - **LogicGate**: gate ID, `defaultPort` are required; each branch must have a non-empty `port` value and a valid condition.
  - **ToolGate**: gate ID, `toolName` are required.
  - **ReadFileGate**: gate ID, `path`, `outputKey` are required.
  - **WriteFileGate**: gate ID, `path`, `contentKey`, `mode` are required.
  - **Pipeline metadata**: `id` and `entry` are required; `name` and `description` are optional.
- FR-28: On the first Apply for any file whose YAML buffer contains top-level comments or non-default key ordering, a one-time dialog must inform the user that applying will normalize the file (comments inside gates may be lost) and offer **Cancel** to abort. If the user accepts, the acceptance must be remembered per-file so the dialog does not reappear for that file.
- FR-29: On successful Apply, the YAML buffer must be overwritten with the canonical representation of the current visual state. The Text tab must reflect this updated buffer immediately. The file must be marked as modified (unsaved indicator) in the IDE but must not be written to disk automatically.

### Tab Synchronization

- FR-30: Switching from the Visual tab to the Text tab must show the current buffer state — the last Applied state if Apply has been clicked, or the original file state otherwise. Unapplied visual edits must not appear in the Text tab or the buffer.
- FR-31: Switching from the Text tab to the Visual tab must re-parse the current buffer and re-render the canvas.
- FR-32: If the Visual tab contains unapplied edits when the user navigates to the Text tab and then back to the Visual tab, a confirmation prompt must ask the user whether to discard those edits and reload from the buffer, or return to the Visual tab without reloading.

### Live Execution Overlay

- FR-33: The Visual tab toolbar must include a **Run** button equivalent in behavior to the gutter run icon.
- FR-33a: If the Visual tab has unapplied edits when the user clicks Run, a confirmation prompt must inform the user that execution will run the last-applied (or original) buffer state, not the unsaved visual state, and ask whether to proceed or cancel. Run does not implicitly Apply.
- FR-34: Clicking **Run** must prompt the user for any required fields defined in the `InputGate`'s `inputSchema` before starting execution.
- FR-35: While the pipeline is running, each node must display a live state badge: a spinning/in-progress indicator on the currently executing gate, a green-check badge on succeeded gates, and a red-X badge on failed gates. Gates not yet reached must show an idle state with no badge.
- FR-36: During execution, a **Cancel** button must replace the **Run** button in the toolbar. Clicking Cancel must stop pipeline execution.
- FR-37: After execution completes (success or failure), state badges must remain visible on all nodes until the user explicitly clears them or starts a new run.

### Layout Sidecar File

- FR-38: Node positions must be persisted in a sidecar file named `<pipeline-filename>.layout.json` co-located with the YAML in the `.flai/` directory (e.g., `my-pipeline.flai.yaml.layout.json`).
- FR-39: The sidecar file must be written when Apply is clicked; it must not be written on drag-only sessions where Apply is never clicked.
- FR-40: The sidecar file must be purely optional for pipeline execution; deleting it must have no effect on pipeline runs.
- FR-41: If the sidecar file is absent or does not contain a position entry for a gate, that gate must be positioned using automatic layout.

---

## User Stories

**US-1 (Engineer — viewing a pipeline):** As an engineer, I want to open a `.flai.yaml` file and switch to a Visual tab so that I can understand the pipeline's graph structure without manually reading YAML.

**US-2 (Engineer — editing gate properties):** As an engineer, I want to click a node and edit its fields in a property panel so that I can change gate configuration without writing raw YAML.

**US-3 (Engineer — building a pipeline):** As an engineer, I want to drag gate types from a palette, connect them by dragging port handles, and delete nodes or edges so that I can compose and restructure a pipeline visually.

**US-4 (Engineer — applying changes):** As an engineer, I want to click Apply to write the current visual state back to the YAML buffer so that my edits are preserved while the YAML file remains the single source of truth.

**US-5 (Engineer — renaming a gate):** As an engineer, I want to rename a gate's ID and have all connected edges update automatically so that I can reorganize the pipeline without manually fixing references.

**US-6 (Engineer — running the pipeline):** As an engineer, I want to trigger a pipeline run from the Visual tab and watch each gate's execution state highlighted in real time so that I can diagnose which gate is executing or has failed.

**US-7 (Engineer — preserving layout):** As an engineer, I want my node positions saved in a sidecar file so that the spatial arrangement I set is restored the next time I open the file, without affecting the YAML.

**US-8 (Engineer — handling parse errors):** As an engineer, I want the Visual tab to show a clear error message when the YAML is malformed so that I understand what to fix in the Text tab.

---

## Acceptance Criteria

**US-1 / FR-1, FR-2, FR-3, FR-4, FR-5:**
- Given a valid `.flai.yaml` file is open, the editor shows both a Text tab and a Visual tab.
- Given the user clicks Visual, a canvas appears with one node per gate; node labels match gate IDs.
- Each gate type is rendered with a visually distinct appearance (color, shape, or icon).
- The gate whose ID matches the `entry` field carries an explicit entry marker (e.g., label, border, badge).
- Edges between gates are drawn as directed arrows; an edge whose `fromPort` equals `out` and `toPort` equals `in` carries no label; an edge with non-default port values carries a label showing those values.

**US-1 / FR-6:**
- Given the YAML buffer contains a parse error, the Visual tab displays a banner with the error text, the canvas is blank, and all editing controls are disabled.
- Correcting the YAML in the Text tab and switching back to Visual renders the canvas successfully.

**US-3 / FR-7, FR-8, FR-9:**
- The palette lists exactly 7 entries: `input`, `output`, `llm`, `logic`, `tool`, `read-file`, `write-file`.
- Dragging any palette entry onto the canvas creates a new node with all required default values, no connected edges, and a unique ID that does not match any existing gate ID.

**US-3 / FR-10:**
- Dragging a node to a new position updates the node's canvas position; the YAML buffer is unchanged until Apply is clicked.

**US-3 / FR-13, FR-14, FR-15:**
- Dragging from an output port handle to an input port handle creates a new edge.
- No edge can be dropped onto an `InputGate` input; the drag target is rejected.
- A `LogicGate` node displays one named handle per branch port plus the `defaultPort` handle; dragging from a branch handle creates an edge with `fromPort` set to that branch's port name.

**US-3 / FR-16, FR-17:**
- Selecting an edge and pressing Delete removes that edge from the canvas; the YAML buffer is unchanged until Apply.
- The system prevents creation of a duplicate edge (same `from`, `fromPort`, `to`, `toPort`).

**US-2 / FR-18, FR-19:**
- Clicking a node opens the property panel; all fields for that gate type are present and labeled.
- For `LlmGate`, `promptTemplate` is a full-size, always-visible multi-line text area with a vertical resize handle; it is never rendered as a single-line input.
- For `LlmGate`, the `endpointConfig.credentialId` field is a plain text input that accepts and displays the credential identifier only; no control for entering or updating the credential secret is present.
- For `LogicGate`, each branch row exposes a condition-type selector; choosing `Comparison` reveals `variable`, `op` (dropdown of 8 ops), and `value`; choosing `SwitchCase` reveals `variable` and a multi-value list; choosing `Always` reveals no additional fields.
- For `LogicGate`, an **Add Branch** button appends a new branch row; each existing branch row has a delete icon. Clicking the delete icon on a branch whose port has a connected edge silently removes both the branch and the edge with no confirmation dialog.
- For `ToolGate`, the `toolName` field is a dropdown; free-text entry is not permitted. The dropdown is populated at panel-open time with the tools registered in the IDE at that moment; tools registered after the panel opens do not appear unless the panel is closed and reopened.
- For `WriteFileGate`, `mode` is a dropdown with exactly three options: `overwrite`, `append`, `fail-if-exists`.
- Map fields (`outputMapping`, `inputMapping`, `endpointConfig.params`) are editable as key/value row pairs with add/remove controls.
- List fields (`inputSchema` rows, `skills`) are editable as ordered lists with add/remove controls.

**US-5 / FR-20, FR-21:**
- Renaming a gate ID cascades to all edges that reference the old ID; no edges are lost.
- Attempting to set a gate ID to an existing gate's ID shows an inline error and the rename is not committed.
- Attempting to set a gate ID to empty shows an inline error and the rename is not committed.

**US-2 / FR-22:**
- Editing a node label in the property panel updates the canvas label immediately; the Text tab content is unchanged.

**US-1 / FR-23:**
- Clicking the canvas background opens the pipeline-level panel showing `id`, `name`, and `description` fields; editing them is reflected immediately in the visual state but not in the buffer until Apply.

**US-1 / FR-24:**
- Right-clicking a node and choosing **Set as Entry** moves the entry marker to that node; the pipeline's `entry` field reflects the new value on the next Apply.

**US-3 / FR-25:**
- Selecting a node and pressing Delete removes the node and all its connected edges; the canvas updates immediately; the YAML buffer is unchanged until Apply.

**US-4 / FR-26, FR-27, FR-28, FR-29:**
- The Apply button is visible at all times in the Visual tab toolbar.
- Clicking Apply when a required field is empty (per the per-gate-type list in FR-27) shows an error banner listing the gate ID and missing field name(s); the YAML buffer is not modified.
- Clicking Apply with a missing pipeline `id` or `entry` shows an error banner; the YAML buffer is not modified.
- On first Apply for a file that has top-level YAML comments, a one-time warning dialog appears; clicking Cancel aborts Apply; accepting proceeds and the dialog does not appear again for that file.
- On successful Apply, the Text tab shows the updated canonical YAML; the file's unsaved indicator is set; the file is not written to disk.

**US-4 / FR-30, FR-31, FR-32:**
- Switching from Visual to Text shows the buffer at its last Applied state (or original state if never Applied), not the unapplied visual edits.
- Switching from Text to Visual re-parses and re-renders the canvas.
- If the Visual tab has unapplied edits and the user switches to Text then back to Visual, a confirmation prompt asks whether to discard edits; choosing to return does not reload the canvas.

**US-6 / FR-33, FR-34, FR-35, FR-36, FR-37:**
- The Visual tab toolbar contains a Run button.
- Clicking Run with an `InputGate` in the pipeline shows an input prompt for each required field before execution starts.
- During execution, each gate node shows the correct badge (in-progress, succeeded, failed, idle); idle gates show no badge.
- A Cancel button replaces Run during execution; clicking it stops the run.
- After execution ends, badges remain until the user clears them or starts a new run.

**US-7 / FR-38, FR-39, FR-40, FR-41:**
- After clicking Apply, a `<pipeline-filename>.flai.yaml.layout.json` file exists in the same `.flai/` directory.
- Deleting the sidecar file has no effect on pipeline execution.
- Opening the file after deleting the sidecar: gates appear with automatic layout positions.
- Dragging nodes and not clicking Apply: no sidecar file is created or modified.

---

## Edge Cases & Error Scenarios

- **EC-1 (Malformed YAML):** If the YAML buffer cannot be parsed at all, the Visual tab shows the error banner. If it can be parsed but fails structural validation (e.g., missing `id`, `gates`, or `entry` field), the same error banner is shown.
- **EC-2 (Entry gate deleted):** If the user deletes the gate whose ID matches the pipeline's `entry` field, the system must handle this at Apply-time validation: Apply must be blocked and the error banner must indicate that the `entry` field references a non-existent gate.
- **EC-3 (Stale sidecar entries):** If the sidecar file references gate IDs that no longer exist in the pipeline, those entries are silently ignored; existing gates with matching IDs use stored positions; others use automatic layout.
- **EC-4 (ID rename collision):** If the user attempts to rename a gate to an ID already used by another gate, the rename is rejected with an inline error; the old ID is preserved.
- **EC-5 (Duplicate edge attempt):** If the user drags an edge that would duplicate an existing (`from`, `fromPort`, `to`, `toPort`) tuple, the edge is not created; no error is required but the drag must visually cancel (e.g., snap back).
- **EC-6 (External file modification):** If the `.flai.yaml` file is modified on disk by an external process while the Visual tab has unapplied edits, the IDE's standard file-changed notification is shown; the behavior on reload follows FR-32 (discard-or-continue prompt).
- **EC-7 (LogicGate branch port renamed):** If a branch port name in the property panel is changed and an edge references the old port name, the edge's `fromPort` must update to the new name automatically (same cascade rule as gate ID rename).
- **EC-7a (LogicGate branch deleted with connected edge):** If a branch row is deleted from a `LogicGate` via the per-row delete icon and that branch's port has a connected edge, the edge is silently removed from the canvas alongside the branch. No confirmation dialog is shown. This is intentional behavior, not an error state.
- **EC-8 (No InputGate in pipeline):** If the pipeline has no `InputGate`, clicking Run must start execution immediately without showing an input prompt.
- **EC-9 (Apply during active run):** While a pipeline run is in progress, the Apply button must be disabled to prevent concurrent modification of the buffer.
- **EC-9a (Canvas editability during run):** While a pipeline run is in progress, all canvas editing operations (node drag, palette drop, property panel edits, edge creation/deletion, gate deletion) must be disabled. The canvas is read-only during execution; only the Cancel button is active.
- **EC-10 (Broken edges at Apply):** If an edge references a `from` or `to` gate ID that does not exist in the current gate list (possible if a gate was deleted and its edges somehow not cleaned up), Apply validation must flag these as errors and block the write.
- **EC-11 (Invalid port at Apply):** If an edge references a `fromPort` that does not exist on its source gate (e.g., a logic branch was deleted but the edge was not), Apply validation must flag this and block the write.
- **EC-12 (Switching tabs with pending edits — user chooses discard):** The canvas reloads from the buffer and all unapplied changes are lost; this is not recoverable.
- **EC-13 (Palette drop outside canvas bounds):** If a gate is dragged from the palette and dropped outside the scrollable canvas area, the drop is cancelled and no gate is created.

---

## Non-Functional Requirements

- NFR-1: The sidecar file must not be required for pipeline execution; the pipeline runs solely from the YAML file.
- NFR-2: The Visual tab must not write any changes to the YAML buffer or to disk without an explicit Apply action from the user.
- NFR-3: The one-time Apply normalization warning must be persisted per file so it does not reappear after the user has accepted it once for that file.

---

## Out of Scope

- Creating a new `.flai.yaml` pipeline file from within the canvas (file creation remains in the standard IDE flow).
- Guaranteed 100% preservation of YAML comments, key ordering, or custom formatting on Apply; only best-effort top-level comment preservation is provided.
- Multi-file or cross-pipeline visualization (edges spanning multiple files).
- Export to any format other than `.flai.yaml`.
- Collaborative or multi-user editing.
- Fine-grained visual undo/redo history beyond IntelliJ's standard undo stack (deferred to Phase 2).
- Multi-select of nodes for bulk move or bulk delete (deferred to Phase 2; MVP supports single-node selection only).
- `.gitignore` management for the sidecar file (team convention, not product behavior).

---

## Questions for PM

_None_
