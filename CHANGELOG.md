<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# flai Changelog

## [Unreleased]

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
