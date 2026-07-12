# flai CLI — Design

Date: 2026-07-12
Status: approved

## Goal

Non-interactive CLI that runs a `.flai.yaml` pipeline with provided inputs, usable in CI.
Published as a fat JAR on GitHub releases alongside the plugin ZIP. Plugin artifacts are
rebuilt/republished only when plugin-affecting code changed.

## Module layout (Clean / hexagonal architecture)

Restructure the single-module build into three Gradle modules:

### `:core` (new, plain Kotlin JVM — zero IntelliJ dependencies)

Moved from the plugin module unchanged in package names:

- `domain/` — models, ports, `GateExecutor`, `PipelineExecutor` service contract
- `usecase/` — `ListPipelinesUseCase`, `LoadPipelineUseCase`, `RunPipelineUseCase`
- `infrastructure/pipeline/` — `YamlPipelineParser`, `YamlPipelineSerializer`, `PipelineValidator`
- `infrastructure/executor/` — `CoroutinePipelineExecutor`, all `DefaultXxxGateExecutor`, `SkillLoader`
- `infrastructure/template/SimpleTemplateRenderer`
- `infrastructure/llm/HttpLlmClient` — refactored: PasswordSafe access extracted behind a new
  domain port `CredentialResolver` (`fun resolve(credentialId: String): String?`), injected via
  constructor.

Dependencies (explicit; today they arrive implicitly from the IntelliJ platform):
`kotlinx-coroutines-core`, `snakeyaml`, `gson`.

### root plugin module (existing)

Keeps `ui/`, `startup/`, IntelliJ-specific infrastructure:
`YamlPipelineRepository` (VFS/Project), `IdeToolRegistry`, `FileReadTool`, `RunCommandTool`,
`PsiSymbolSearchTool`, and a new `PasswordSafeCredentialResolver` implementing `CredentialResolver`.

Depends on `implementation(project(":core"))` with `kotlinx-coroutines`, `gson`, `snakeyaml`,
and kotlin-stdlib excluded (the platform provides them; bundling coroutines is forbidden).

### `:cli` (new)

Command shape (non-interactive, no prompts ever):

```
java -jar flai-cli.jar run <pipeline-file> \
    [--input key=value]... \
    [--inputs-json <file>] \
    [--workdir <dir>] \
    [--format text|json] \
    [--quiet]
```

- `run` — the only command for now. `--input` repeats; `--inputs-json` merges a JSON object file
  (explicit `--input` wins on key conflict).
- `--workdir` — base dir for bash/read/write gates and relative tool paths; defaults to the
  pipeline file's directory's project root heuristic: the pipeline file's parent if it is not
  `.flai`, else the `.flai` dir's parent.
- `--format text` (default) streams execution events human-readably to stderr and prints final
  outputs to stdout; `json` prints a single JSON object of final outputs to stdout.
- `--quiet` suppresses event stream.
- Exit codes: `0` success, `1` pipeline execution failure, `2` usage/parse error.

Adapters:

- `FilePipelineRepository` — loads a single pipeline from an explicit file path (plain `java.io.File`).
- `EnvCredentialResolver` — resolves `credentialId` → env var `FLAI_CREDENTIAL_<ID>` where `<ID>`
  is the id uppercased with every non-alphanumeric char replaced by `_`.
- `CliToolRegistry` + `CliFileReadTool`, `CliRunCommandTool` (workdir-based, no Project).
  PSI symbol search is not available; a pipeline referencing it fails with a clear message.

Arg parsing: hand-rolled plain Kotlin (single command, YAGNI — no CLI framework dependency).

Artifact: fat JAR `flai-cli-<version>.jar` via `com.gradleup.shadow`, `Main-Class` manifest.

## CI / release workflow

- `build.yml`: unchanged in spirit — `./gradlew build` now compiles and tests all three modules.
- `release.yml`:
  - New change-detection step: `git diff --name-only <previous-release-tag>..<tag>`.
  - Plugin steps (buildPlugin, upload ZIP, publishPlugin) run only if diff touches plugin paths:
    `src/`, `core/`, `build.gradle.kts`, `core/build.gradle.kts`, `gradle.properties`,
    `settings.gradle.kts`, `gradle/`.
  - CLI steps (shadowJar, upload `flai-cli-*.jar`) run only if diff touches `cli/`, `core/`,
    or gradle files.
  - No previous release tag → both run.

## Testing

Unit tests in `:cli`:

- Arg parser: valid combinations, repeated `--input`, conflict precedence, unknown flag → usage error.
- `EnvCredentialResolver`: id mangling, missing var → null.
- `FilePipelineRepository`: loads file, missing file → load exception.
- End-to-end: run a temp pipeline (input → logic → output gates only, no network) through the real
  executor stack, assert final outputs and exit code mapping.

`:core` keeps any existing tests that move with the code.

## Error handling

- Usage errors → stderr message + exit 2, never stack traces.
- Pipeline load/validation errors → stderr message + exit 1.
- Gate failure → event stream shows failing gate + error, exit 1.
- `CancellationException` rethrown per project convention.
