# Dev Report: Fault-Tolerant Gate

## Implemented
Added an opt-in, per-gate `faultTolerant` flag (default `false`). When a gate marked fault tolerant fails at runtime, the pipeline records a distinct tolerated-failure trace entry and continues along the gate's normal outgoing edge instead of aborting. Default-off preserves existing fail-fast behavior.

Files modified:
- `src/main/kotlin/me/drew/flai/domain/model/Gate.kt` — added abstract `faultTolerant: Boolean` to `Gate`; added `override val faultTolerant: Boolean = false` as the last constructor parameter of all eight gate data classes.
- `src/main/kotlin/me/drew/flai/domain/model/ExecutionContext.kt` — added `TOLERATED_FAILURE` to `TraceStatus`.
- `src/main/kotlin/me/drew/flai/infrastructure/executor/CoroutinePipelineExecutor.kt` — split the `GateResult.Failure` branch in `handleResult`: when `gate.faultTolerant`, emit a `TOLERATED_FAILURE` trace entry and continue (port = `defaultPort ?: "out"` for `LogicGate`, `"out"` otherwise); otherwise unchanged throw. Blank failure reasons fall back to `"no reason available"`.
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParser.kt` — read `faultTolerant` once at the top of `parseGate` via the existing `parseBoolean` helper (defaults to `false`); passed into all eight gate constructors.
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializer.kt` — emit `faultTolerant: true` once (raw literal, only when true) after the `when(gate)` block in `appendGate`.
- `src/main/kotlin/me/drew/flai/ui/model/UiModels.kt` — added `TOLERATED_FAILURE` to `GateStatus`.
- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt` — extracted the trace→gate status mapping into a top-level `internal fun traceStatusToGateStatus` (mirroring the existing `mergeInputs` pattern) with an explicit `TraceStatus.TOLERATED_FAILURE -> GateStatus.TOLERATED_FAILURE` branch so it never falls through `else -> SUCCESS`; `handleEvent` calls it.
- `src/main/kotlin/me/drew/flai/ui/toolwindow/ExecutionLogPanel.kt` — added a `GateStatus.TOLERATED_FAILURE` renderer branch (warning icon + bold orange label).
- `src/main/kotlin/me/drew/flai/ui/visual/CanvasRenderer.kt` — added a `GateStatus.TOLERATED_FAILURE` status-badge branch (orange, distinct from success green and failure red). Not in the architecture file plan but a forced exhaustive-`when` compile site; see Deviations.
- `docs/pipeline-yaml-spec.md` — documented the `faultTolerant` field, its default, that it applies to all gate types, and runtime behavior (tolerated failure recorded distinctly, no outputs contributed, continuation along `out`/`defaultPort`).

Files created:
- `src/test/kotlin/me/drew/flai/infrastructure/executor/CoroutinePipelineExecutorFaultTolerantTest.kt` — 14 executor tests covering AC-2..AC-6, AC-9, FR-11, and all listed edge cases (entry-gate fault tolerant, no outgoing edge, two tolerated failures, fail-fast still applies downstream, blank reason, per-gate-type continuation, logic `defaultPort` vs null-`defaultPort`). The logic `defaultPort` continuation case uses a fake `GateExecutor` returning `Failure` for a `LogicGate` with a non-null `defaultPort`, since the real `DefaultLogicGateExecutor` only fails when `defaultPort == null`.
- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParserFaultTolerantTest.kt` — parser tests (true / false / absent-defaults-false / non-boolean raises `PipelineLoadException`).
- `src/test/kotlin/me/drew/flai/ui/service/TraceStatusToGateStatusTest.kt` — UI mapping tests guarding FR-9/AC-7: `TOLERATED_FAILURE` maps to `GateStatus.TOLERATED_FAILURE` (never `SUCCESS`) and stays distinct from `FAILURE`.

Files modified (tests):
- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializerTest.kt` — added a round-trip test asserting `faultTolerant = true` survives parse→serialize→parse for all eight gate types (also guards the visual-editor save path and catches any missed parser/serializer gate branch), plus a test that `false` omits the key.

Full `./gradlew test` passes; existing executor and serializer tests pass unchanged (NFR-1 / AC-8 backward-compatibility evidence).

## Deviations
- The architecture file plan did not list `CanvasRenderer.kt`, but its `drawStatusBadge` has an exhaustive `when (status: GateStatus)` that fails to compile once `GateStatus.TOLERATED_FAILURE` is added. Added an orange badge branch there (distinct from success/failure), consistent with FR-8's "visually distinct" intent. No behavior change for existing statuses.
- `ExecutionLogPanel` uses a custom `SimpleTextAttributes(STYLE_BOLD, JBColor.ORANGE)` for the tolerated-failure label rather than `SimpleTextAttributes.WARNING_ATTRIBUTES` (which does not exist on this platform version).
- The trace→gate status mapping was extracted from `handleEvent` into a top-level `internal fun traceStatusToGateStatus`, following the existing `mergeInputs` precedent (a top-level `internal fun` extracted so `MergeInputsTest` can test it without an IntelliJ fixture). This is the architecture's listed Test Strategy item and the mitigation for its #1 risk (silent FR-9 violation via `else -> SUCCESS`); it is now directly unit-tested in `TraceStatusToGateStatusTest`.

## Follow-ups
None.

## Questions for Architect
_None_
