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

# Run core/cli module tests
./gradlew :core:test :cli:test

# Build the CLI fat JAR (-> cli/build/libs/flai-cli-<version>.jar)
./gradlew :cli:fatJar
```

Target IDE: IntelliJ IDEA 2025.2.6.1 (via `intellijPlatform` block in `build.gradle.kts`).

## Modules

Three Gradle modules:

- **`core/`** — plain Kotlin JVM, zero IntelliJ deps: `domain/`, `usecase/`, and pure infrastructure (parser, serializer, validator, gate executors, `SimpleTemplateRenderer`, `SkillLoader`, `HttpLlmClient`, `DefaultToolRegistry`). Explicit deps: coroutines, snakeyaml, gson.
- **root** — the IntelliJ plugin: `ui/`, `startup/`, IntelliJ adapters. Depends on `:core` with coroutines/gson/snakeyaml/stdlib excluded (the platform provides them).
- **`cli/`** — non-interactive CLI runner (`flai-cli` fat JAR); see [`docs/cli.md`](docs/cli.md). CLI adapters live in `me.drew.flai.cli.adapter`.

## Architecture

Hexagonal architecture (ports & adapters). Layers:

**`core/.../domain/`** — pure Kotlin, no IntelliJ deps
- `model/` — `Pipeline`, `Gate` (sealed: Input/Output/Llm/Logic/Tool), `ExecutionContext`, `GateResult`
- `port/` — interfaces: `LlmClient`, `PipelineRepository`, `TemplateRenderer`, `ToolRegistry`, `CredentialResolver`
- `executor/GateExecutor<G>` — typed interface: `canHandle()` + `execute()`
- `service/PipelineExecutor` — emits `Flow<ExecutionEvent>`

**`core/.../infrastructure/`** — pure IO implementations
- `executor/` — one `DefaultXxxGateExecutor` per gate type; `CoroutinePipelineExecutor` walks the graph via `channelFlow`, dispatches to matching executor
- `llm/HttpLlmClient` — supports both Anthropic and OpenAI response shapes; API keys via injected `CredentialResolver`
- `pipeline/YamlPipelineParser` — pipelines live in `<project>/.flai/*.flai.yaml`
- `tool/DefaultToolRegistry` — plain tool registry

**root `infrastructure/`** — IntelliJ adapters
- `pipeline/YamlPipelineRepository` — VFS-backed repository
- `credential/PasswordSafeCredentialResolver` — `CredentialResolver` over IntelliJ `PasswordSafe`
- `tool/` — `PsiSymbolSearchTool`, `FileReadTool`, `RunCommandTool` registered at startup

**`cli/`** adapters — `FilePipelineRepository`, `EnvCredentialResolver` (`FLAI_CREDENTIAL_<ID>` env vars), `CliFileReadTool`, `CliRunCommandTool`

**`ui/`** — Swing + coroutines, all state managed in `FlaiPipelineUiService` (project-level `@Service`)
- `toolwindow/` — `PipelineToolWindowFactory` → `PipelinePanel` (splits list + detail + log)
- `editor/FlaiRunLineMarkerContributor` — gutter run icon on `*.flai.yaml` files
- `FlaiPipelineUiService` — owns `StateFlow`s for pipelines, selection, execution state, log rows; calls use cases

**`core/.../usecase/`** — thin orchestration: `ListPipelinesUseCase`, `LoadPipelineUseCase`, `RunPipelineUseCase`

**`startup/FlaiStartupActivity`** — registers tools into the tool registry then calls `uiService.refresh()`

## Pipeline YAML schema

Full spec: [`docs/pipeline-yaml-spec.md`](docs/pipeline-yaml-spec.md)

Files must end in `.flai.yaml` and live in `<project root>/.flai/`. Required top-level keys: `id`, `name`, `entry`, `gates`, `edges`.

Gate types: `input` | `output` | `llm` | `logic` | `tool`

`LlmGate` endpoint credentials are stored in IntelliJ `PasswordSafe` under service name `"flai/<credentialId>"`; the CLI resolves the same `credentialId` from `FLAI_CREDENTIAL_<ID>` env vars instead.

## Documentation

Docs live in `docs/`. After any task that adds or changes features, gates, tools, or YAML schema:
- Update the relevant existing doc if it covers the changed area.
- Create a new doc in `docs/` if the topic is not yet covered.
- Keep `docs/pipeline-yaml-spec.md` in sync with any gate or schema changes.

## Code style

- Always use braces for `if`/`else`/`for`/`while` bodies, even single-line; every statement inside braces must be on its own line
- Never use semicolons (`;`) to separate statements — use newlines instead
- No unused imports, fields, or functions — remove dead code immediately
- Catch `CancellationException` before `Exception` in `suspend` functions and rethrow it; never swallow it
- Use `?: throw IllegalStateException(...)` instead of `!!` for nullable platform values (e.g. `project.basePath`)
- Track `Job` references for coroutines launched on repeated calls (e.g. panel rebuilds) and cancel the previous job before launching a new one
- Services that own a `CoroutineScope` must implement `Disposable` and cancel the scope in `override fun dispose()`

## Key conventions

- New gate type → add sealed subclass in `Gate.kt`, a `DefaultXxxGateExecutor`, add parsing in `YamlPipelineParser.parseGate()` (all in `:core`), wire it in `FlaiPipelineUiService` AND `CliRunner`
- New IDE tool → implement in root `infrastructure/tool/`, register in `FlaiStartupActivity`; CLI counterpart (if applicable) goes in `cli/.../adapter/` and is registered in `CliRunner`
- UI state changes go through `FlaiPipelineUiService` `StateFlow`s — panels observe, never mutate state directly
- `ExecutionContext.applyOutputs()` handles both explicit `outputMapping` and pass-through (empty mapping stores outputs directly)
