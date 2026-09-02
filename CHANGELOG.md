<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# flai Changelog

## [Unreleased]

## [0.6.1]
### Added
- Tool window: the pipeline list now follows the files in `.flai/` on disk — files created, saved, renamed, moved or deleted show up without pressing Refresh (debounced, silent, never interrupts a run).
- Visual editor: changes are now written to the pipeline YAML automatically (debounced) and saved to disk; `Apply` remains for full validation.
- Visual editor: empty pipeline files get a pipeline `id`/`name` derived from the file name, and the first dropped gate becomes the `entry` gate.
- Tool window: a pipeline that parses but fails validation (for example a gate the visual editor wrote before it was configured) is marked in the list with a warning icon, an `N issues` label and a tooltip listing every problem, and the detail panel shows the same list above the inputs. Run stays available and reports the problems in the execution log.

### Changed
- Pipeline validation unified in core `PipelineValidator` (used by runner, CLI and visual editor): gate field rules, port/edge checks, one edge per output port, cycles. The runner now rejects pipelines the editor would reject and vice versa.
- Visual editor sync moved out of the file editor into `VisualPipelineDocumentSync`; node layout and preferences are infrastructure adapters.

### Fixed
- Visual editor: auto-sync no longer fails with "Access is allowed from write thread only" when saving the document.
- The "run failed" message of an earlier run no longer sticks around after selecting another pipeline: switching pipelines clears the execution log and result in the tool window and hides the visual editor's run-failure banner. A run in progress is never interrupted.
- Visual editor: a gate dropped on the canvas is now saved to the YAML file immediately, before its fields are filled in. Gate field rules (LLM prompt/endpoint, tool name, bash command, file paths) are reported by Apply and Run instead of blocking the write, and the parser accepts the half-configured YAML the editor writes.
- Visual editor: new pipeline files could not be applied or run because the pipeline had no `id` and no `entry`.
- Visual editor: Run now flushes pending visual edits before running instead of discarding them.
- Visual editor: a `#` inside a prompt or command no longer blocks auto-sync with a bogus "YAML comments" warning; the normalize confirmation only appears when writing would actually reformat the file.
- Visual editor: saving no longer wipes undo history or stale-selects nodes when a save-time formatter touches the document.
- Visual editor: running one pipeline no longer locks every open visual editor.
- Visual editor: toggling an LLM tool checkbox no longer opens the full Apply validation dialog.
- Pipeline repository: a pipeline id like `foo.flai` no longer resolves to `foo.flai.yaml`; file name matching is case-insensitive.

## [0.6.0]
### Added
- LLM gates can allow registered tools and run iterative tool-call conversations with OpenAI and Anthropic endpoints, with a configurable maximum number of tool rounds.
- The visual pipeline editor now provides an LLM provider selector, tool allowlist controls, and a maximum tool-round setting.
- Execution logs report each LLM tool call's name, round, status, and duration while keeping arguments and results redacted.

### Changed
- Built-in IDE and CLI tools now publish input schemas so LLM providers can validate tool-call arguments.

### Fixed
- Pipeline reload now discovers `.flai`, `.flai.yml`, `.yml`, `.flai.yaml`, and `.yaml` files.

## [0.5.0]
### Changed
- Refreshed plugin branding: new plugin icon (`pluginIcon.svg`) plus updated `.flai.yaml` file type and tool window icons (light and dark variants).
- README now documents the `flai-cli` non-interactive command-line runner.

### Dependencies
- Bump `org.jetbrains.intellij.platform` Gradle plugin from 2.16.0 to 2.18.0.
- Bump Gradle wrapper from 9.4.1 to 9.6.1.

## [0.4.0]
### Added
- `flai-cli`: non-interactive command-line pipeline runner for CI (`java -jar flai-cli.jar run <pipeline> --input key=value`), published as a fat JAR on GitHub releases. See `docs/cli.md`.

### Changed
- Internal restructuring into three Gradle modules: `core` (pure domain + executors), the IntelliJ plugin, and `cli`. Plugin release workflow now republishes the plugin only when plugin-affecting code changed.

### Fixed
- Canvas node decorations (accent stripe, entry badge, LLM star) are clipped to the rounded node body and no longer poke past the corners; the entry badge is now a corner wedge that follows the corner curve instead of a floating triangle.
- Minimap projection matches the canvas transform, so the minimap no longer drifts or misscales at different zoom levels.
- Zoom buttons no longer go missing from the pipeline editor overlay.
- Property panel rows size to their field height instead of being cut off, and the panel scrolls correctly.

## [0.3.0]
### Added
- Plugin icons: dedicated icons for the `.flai.yaml` file type, the flai tool window (light and dark variants), and pipeline/tool entries in the pipeline list.

### Fixed
- Gate status color line no longer bleeds over the properties panel — canvas painting is clipped to the canvas bounds.

## [0.2.0]
### Added
- Fault-tolerant gates: opt-in per-gate `faultTolerant` flag. When a fault-tolerant gate fails, the pipeline continues along its normal outgoing edges instead of aborting. Disabled by default, so existing pipelines are unchanged.
- "Fault Tolerant" checkbox in the node property panel, available for every gate type.
- Tolerated failures are surfaced distinctly — a warning icon and orange badge in the execution log and on the canvas node — never shown as success.

### Fixed
- Execution log rows and canvas status badges now match gates by id instead of label, so pipelines with duplicate gate labels render each gate's status independently.

## [0.1.0]
### Added
- Visual pipeline editor with drag-and-drop canvas, node palette, and property panels
- Pipeline execution engine with coroutine-based gate execution and live log panel
- Gate types: `input`, `output`, `llm`, `logic`, `tool`, `bash`, `read-file`, `write-file`
- LLM gate with Anthropic and OpenAI endpoint support; credentials stored in IntelliJ PasswordSafe
- Logic gate with branching conditions (always, comparison, switch-case)
- Bash gate with configurable working directory, timeout, and environment variables
- Read/write file gates
- Skills (prompt file injection) support for LLM gates
- YAML pipeline format (`.flai.yaml`) with full serialization and parser
- Gutter run icon on `.flai.yaml` files
- Tool window with pipeline list, input fields, run button, and execution log
- Auto-layout for pipeline nodes
- Zoom controls with lock, minimap overlay
- Undo/redo support in visual editor
- Cmd+S applies visual changes to YAML
- Input values persisted per pipeline across sessions
- Double-click on output variable shows value popup
