# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build plugin
./gradlew build

# Run plugin in sandboxed IDE
./gradlew runIde

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "me.drew.flai.MyPluginTest"

# Build distributable ZIP
./gradlew buildPlugin

# Verify plugin against IDE compatibility
./gradlew verifyPlugin
```

Target IDE: IntelliJ IDEA 2025.2.6.1 (via `intellijPlatform` block in `build.gradle.kts`).

## Architecture

Hexagonal architecture (ports & adapters). Three layers:

**`domain/`** — pure Kotlin, no IntelliJ deps
- `model/` — `Pipeline`, `Gate` (sealed: Input/Output/Llm/Logic/Tool), `ExecutionContext`, `GateResult`
- `port/` — interfaces: `LlmClient`, `PipelineRepository`, `TemplateRenderer`, `ToolRegistry`
- `executor/GateExecutor<G>` — typed interface: `canHandle()` + `execute()`
- `service/PipelineExecutor` — emits `Flow<ExecutionEvent>`

**`infrastructure/`** — IntelliJ/IO implementations
- `executor/` — one `DefaultXxxGateExecutor` per gate type; `CoroutinePipelineExecutor` walks the graph via `channelFlow`, dispatches to matching executor
- `llm/HttpLlmClient` — supports both Anthropic and OpenAI response shapes; credentials via IntelliJ `PasswordSafe` keyed by `credentialId`
- `pipeline/YamlPipelineParser` + `YamlPipelineRepository` — pipelines live in `<project>/.flai/*.flai.yaml`
- `tool/IdeToolRegistry` — `PsiSymbolSearchTool`, `FileReadTool`, `RunCommandTool` registered at startup

**`ui/`** — Swing + coroutines, all state managed in `FlaiPipelineUiService` (project-level `@Service`)
- `toolwindow/` — `PipelineToolWindowFactory` → `PipelinePanel` (splits list + detail + log)
- `editor/FlaiRunLineMarkerContributor` — gutter run icon on `*.flai.yaml` files
- `FlaiPipelineUiService` — owns `StateFlow`s for pipelines, selection, execution state, log rows; calls use cases

**`usecase/`** — thin orchestration: `ListPipelinesUseCase`, `LoadPipelineUseCase`, `RunPipelineUseCase`

**`startup/FlaiStartupActivity`** — registers tools into `IdeToolRegistry` then calls `uiService.refresh()`

## Pipeline YAML schema

Full spec: [`docs/pipeline-yaml-spec.md`](docs/pipeline-yaml-spec.md)

Files must end in `.flai.yaml` and live in `<project root>/.flai/`. Required top-level keys: `id`, `name`, `entry`, `gates`, `edges`.

Gate types: `input` | `output` | `llm` | `logic` | `tool`

`LlmGate` endpoint credentials are stored in IntelliJ `PasswordSafe` under service name `"flai/<credentialId>"`.

## Documentation

Docs live in `docs/`. After any task that adds or changes features, gates, tools, or YAML schema:
- Update the relevant existing doc if it covers the changed area.
- Create a new doc in `docs/` if the topic is not yet covered.
- Keep `docs/pipeline-yaml-spec.md` in sync with any gate or schema changes.

## Key conventions

- New gate type → add sealed subclass in `Gate.kt`, a `DefaultXxxGateExecutor`, wire it in `FlaiPipelineUiService`, add parsing in `YamlPipelineParser.parseGate()`
- New IDE tool → implement in `infrastructure/tool/`, register in `FlaiStartupActivity`
- UI state changes go through `FlaiPipelineUiService` `StateFlow`s — panels observe, never mutate state directly
- `ExecutionContext.applyOutputs()` handles both explicit `outputMapping` and pass-through (empty mapping stores outputs directly)
