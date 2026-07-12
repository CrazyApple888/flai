<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# flai Changelog

## [Unreleased]

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
