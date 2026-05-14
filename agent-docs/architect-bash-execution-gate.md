# Bash Execution Gate Architecture

## Slug
bash-execution-gate

## Technical Summary

Add `BashGate` as a first-class sealed `Gate` subtype in the domain model, parse and serialize it as YAML `type: bash`, execute it with a dedicated `DefaultBashGateExecutor`, and wire that executor into `FlaiPipelineUiService`.

The executor will run one non-interactive Bash command with `/bin/bash -lc <rendered command>`, resolve command, working directory, and environment templates from `ExecutionContext`, constrain the working directory to the IntelliJ project root subtree, enforce timeout and cancellation, and return structured outputs: `stdout`, `stderr`, `exitCode`, `success`, and `timedOut`.

Because this repository has a visual pipeline editor and exhaustive `when` expressions over the sealed `Gate` hierarchy, the feature must also update serializer round-tripping, visual palette creation, property editing, validation, icons/theme fallback, and port handling so Bash gates remain usable in both YAML and visual workflows.

## File-Level Plan

- `src/main/kotlin/me/drew/flai/domain/model/Gate.kt`
  - Add `data class BashGate` with fields:
    - `id: GateId`
    - `label: String`
    - `command: String`
    - `workingDirectory: String = "."`
    - `environment: Map<String, String> = emptyMap()`
    - `timeoutSeconds: Int = 120`
    - `failOnNonZeroExit: Boolean = true`
    - `outputMapping: Map<String, String> = emptyMap()`

- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParser.kt`
  - Add a `bash` branch in `parseGate()`.
  - Validate `command` is present and a non-blank string at parse time where possible.
  - Validate optional field types strictly instead of relying on `toString()` for Bash fields.
  - Add helper parsing for `environment`, positive integer `timeoutSeconds`, boolean `failOnNonZeroExit`, and `outputMapping`.

- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializer.kt`
  - Add `BashGate` serialization.
  - Omit default fields for concise YAML: `workingDirectory: "."`, empty `environment`, `timeoutSeconds: 120`, `failOnNonZeroExit: true`, empty `outputMapping`.
  - Use existing scalar and multiline helpers for `command` and map values.

- `src/main/kotlin/me/drew/flai/infrastructure/executor/DefaultBashGateExecutor.kt`
  - New executor implementing `GateExecutor<BashGate>`.
  - Use `TemplateRenderer` plus explicit preflight checks for missing `{{variable}}` references, matching read/write-file gate behavior.
  - Resolve and validate working directory under canonical project root.
  - Start process via `ProcessBuilder("/bin/bash", "-lc", renderedCommand)`.
  - Capture stdout and stderr concurrently as UTF-8 text.
  - Enforce timeout with coroutine timeout primitives and terminate the process on timeout or cancellation.
  - Return `GateResult.Success` for exit code `0`, or non-zero exit when `failOnNonZeroExit` is `false`.
  - Return `GateResult.Failure` for start failure, invalid runtime config, timeout, or non-zero exit when `failOnNonZeroExit` is `true`.

- `src/main/kotlin/me/drew/flai/infrastructure/executor/CoroutinePipelineExecutor.kt`
  - Include `BashGate -> gate.outputMapping` in `applyAndTrace()`.
  - Keep Bash gates on the normal `"out"` edge after success.

- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt`
  - Add `DefaultBashGateExecutor(projectBasePath, renderer)` to the executor list.

- Visual editor files:
  - `src/main/kotlin/me/drew/flai/ui/visual/GatePalettePanel.kt`: add `"bash"` to gate types and map it to an icon.
  - `src/main/kotlin/me/drew/flai/ui/visual/PipelineCanvas.kt`: create a default `BashGate`.
  - `src/main/kotlin/me/drew/flai/ui/visual/NodePropertyPanel.kt`: route `BashGate` to a new property section.
  - `src/main/kotlin/me/drew/flai/ui/visual/GatePropertySections.kt`: add Bash fields for command, working directory, timeout, fail-on-non-zero, environment map, and output mapping; update `rebuildWithLabel()`.
  - `src/main/kotlin/me/drew/flai/ui/visual/VisualPipelineModel.kt`: update `rebuildGateWithId()`.
  - `src/main/kotlin/me/drew/flai/ui/visual/VisualPipelineValidator.kt`: validate command, timeout, and optional mapping/env basics.
  - `src/main/kotlin/me/drew/flai/ui/visual/FlaiEditorTheme.kt` and `CanvasRenderer.kt`: add Bash color/icon handling. Reuse `FlaiIcons.GATE_TOOL` if no dedicated terminal icon exists.

- `docs/pipeline-yaml-spec.md`
  - Add `bash` to top-level gate type list.
  - Add a Bash gate section with fields, defaults, template behavior, output keys, working-directory scope, timeout behavior, non-zero exit behavior, and an example.
  - Update execution model or built-in tool comparison only if needed to clarify the difference from `ide.runCommand`.

- Tests:
  - Add `DefaultBashGateExecutorTest`.
  - Extend `YamlPipelineSerializerTest` for Bash round-trip and full all-gates round-trip.
  - Add parser tests for missing/invalid Bash fields.
  - Update visual tests that enumerate all gate types.

## APIs/Interfaces

Domain model:

```kotlin
data class BashGate(
    override val id: GateId,
    override val label: String,
    val command: String,
    val workingDirectory: String = ".",
    val environment: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 120,
    val failOnNonZeroExit: Boolean = true,
    val outputMapping: Map<String, String> = emptyMap(),
) : Gate()
```

Executor constructor:

```kotlin
class DefaultBashGateExecutor(
    private val projectRoot: String,
    private val renderer: TemplateRenderer,
) : GateExecutor<BashGate>
```

Executor output contract:

```kotlin
mapOf(
    "stdout" to stdoutText,
    "stderr" to stderrText,
    "exitCode" to exitCode,
    "success" to (exitCode == 0),
    "timedOut" to false,
)
```

For timeout failures, do not rely on downstream output mapping because `GateResult.Failure` halts the pipeline. The error message should name the gate and timeout duration.

YAML surface:

```yaml
run-tests:
  type: bash
  label: Run Tests
  command: "./gradlew test --tests {{test_class}}"
  workingDirectory: "."
  timeoutSeconds: 300
  failOnNonZeroExit: true
  environment:
    FLAI_RUN_ID: "{{run_id}}"
  outputMapping:
    stdout: test_stdout
    stderr: test_stderr
    exitCode: test_exit_code
    success: tests_passed
```

## Data Model

`BashGate.command` is the required command template. It is rendered immediately before execution against `context.snapshot()`.

`BashGate.workingDirectory` stores the configured directory template. Use `"."` as the domain default so omitted YAML naturally means project root. The rendered value must be non-blank. Relative paths resolve against project root; absolute paths are accepted only when their canonical resolved path starts with the canonical project root.

`BashGate.environment` is a string map. Values may contain templates. Keys should remain literal environment variable names; they should be validated as non-blank strings. The executor inherits the IDE process environment and overlays rendered gate entries.

`BashGate.timeoutSeconds` is an integer with a default of `120`; values must be greater than zero.

`BashGate.failOnNonZeroExit` defaults to `true`. When `false`, non-zero exit is a successful gate result structurally, with `success = false` in outputs so logic gates can route downstream.

`BashGate.outputMapping` follows the same executor-output-key to context-key convention as LLM and tool gates. Empty mapping stores all raw Bash outputs directly into the context through `ExecutionContext.applyOutputs()`.

## Implementation Order

1. Add `BashGate` to `Gate.kt`.
2. Update every exhaustive sealed `when` over `Gate` to compile with the new subtype:
   - `CoroutinePipelineExecutor.applyAndTrace()`
   - `YamlPipelineSerializer.appendGate()`
   - `VisualPipelineModel.rebuildGateWithId()`
   - `NodePropertyPanel.showGate()`
   - `GatePropertySections.rebuildWithLabel()`
   - `FlaiEditorTheme.accentFor()`
   - `CanvasRenderer.gateIcon()`
   - `VisualPipelineValidator.validate()`
3. Add parser support in `YamlPipelineParser` for `type: bash`.
4. Add serializer support and round-trip tests.
5. Implement `DefaultBashGateExecutor`.
6. Wire `DefaultBashGateExecutor(projectBasePath, renderer)` into `FlaiPipelineUiService`.
7. Add focused executor tests for success, templating, env, working directory scoping, non-zero exit handling, timeout, and cancellation/process cleanup.
8. Update visual editor support: palette, default node creation, property section, validation, theme/icon handling, and related tests.
9. Update `docs/pipeline-yaml-spec.md`.
10. Run `./gradlew test`; then run `./gradlew build` if tests pass or if compilation coverage is needed beyond tests.

## Test Strategy

- Parser tests:
  - Parses minimal Bash gate with defaults.
  - Rejects missing `command`.
  - Rejects blank `command`.
  - Rejects non-map `environment`.
  - Rejects non-string environment values.
  - Rejects non-positive `timeoutSeconds`.
  - Rejects non-boolean `failOnNonZeroExit`.
  - Keeps existing gate types parsing unchanged.

- Serializer tests:
  - Round-trip minimal Bash gate.
  - Round-trip all optional Bash fields.
  - Omit default Bash fields from YAML.
  - Update the existing full round-trip test from all 7 gate types to all 8 gate types.

- Executor tests:
  - `printf hello` captures `stdout`, empty `stderr`, `exitCode = 0`, `success = true`, `timedOut = false`.
  - Command template renders context variables and fails before start when a referenced variable is missing.
  - Environment value template is passed to the process.
  - Working directory relative to project root affects command execution.
  - `../outside` and absolute paths outside project root fail before process start.
  - Command writing to stderr with exit `0` succeeds and captures stderr separately.
  - Non-zero exit fails when `failOnNonZeroExit = true`.
  - Non-zero exit succeeds structurally when `failOnNonZeroExit = false`.
  - Timeout terminates the process and returns failure.
  - Cancellation terminates the process and rethrows `CancellationException`.

- Pipeline integration test:
  - Build a small in-memory pipeline `input -> bash -> output`, verify output mapping reaches final outputs.
  - Build a non-zero Bash gate with `failOnNonZeroExit = true`, verify pipeline emits failure and does not continue.

- Visual tests:
  - Palette filtering includes `bash`.
  - Gate ports for `BashGate` are `in` and `out`.
  - Visual validator catches empty command and invalid timeout.
  - Theme returns a distinct accent for Bash or at least a non-null one.

## Tech Decisions

- Use `/bin/bash -lc` explicitly because the feature is Bash-specific and the functional spec excludes PowerShell/cmd support.
- Use `ProcessBuilder` instead of the existing `RunCommandTool` so the gate remains typed, supports gate-native output mapping, and can implement Bash-specific timeout, failure, and project-root scoping semantics.
- Keep default working directory as `"."` in the domain model to avoid nullable handling and align with project-root default behavior.
- Render command, working directory, and environment values at runtime from `ExecutionContext.snapshot()`. Continue the repository's existing explicit missing-template-variable preflight checks instead of changing `SimpleTemplateRenderer` behavior globally.
- Do not log the rendered environment. Error messages may include gate label/id, exit code, timeout duration, and a concise stderr snippet for failed commands.
- Truncate stderr included in failure messages to a small diagnostic limit, for example 2,000 characters, while preserving full captured streams in successful outputs.
- Use coroutine cancellation discipline already required by the repository: catch `CancellationException` before `Exception`, terminate the process, then rethrow.
- Capture stdout/stderr concurrently to avoid deadlocks when one stream fills while the process is still running.
- Do not add a UI confirmation dialog in this feature; it was explicitly out of scope in the functional spec.

## Questions for System Business Analyst

_None_
