# Architecture: Fault-Tolerant Gate

## Overview
This feature adds an opt-in, per-gate `faultTolerant` flag. When a gate marked fault tolerant fails at runtime, the pipeline records the failure distinctly and continues along that gate's normal outgoing edge instead of aborting. Default is off, preserving today's fail-fast behavior for all existing pipelines.

The change is almost entirely in two layers:

- **`domain/`** — add a `faultTolerant: Boolean` property to the `Gate` sealed hierarchy, and a new `TraceStatus.TOLERATED_FAILURE`. These are pure-data changes with no IntelliJ dependency.
- **`infrastructure/`** — the failure-handling branch in `CoroutinePipelineExecutor.handleResult` is where the actual behavior lives. The YAML parser reads the flag; the YAML serializer writes it (round-trip / visual-editor save preservation).
- **`ui/`** — a new `GateStatus.TOLERATED_FAILURE` rendered distinctly in the execution log, plus a fix to the `TraceStatus → GateStatus` mapping in `FlaiPipelineUiService` so a tolerated failure is never mapped to success.

The visual editor (`VisualPipelineModel`) stores the whole `Gate` object by reference and round-trips it via `toPipeline()`, so the new flag is preserved through open/save automatically once it is a property on the gate data classes — no editor model changes required. Exposing the flag for *editing* in the property panel is explicitly out of scope.

## Tech Decisions

**Decision:** Add `faultTolerant: Boolean` as an abstract property on the `Gate` base class, with a `= false` default on every concrete subclass constructor.
**Reason:** FR-7 requires the flag on every gate type uniformly. Putting it on the base class lets `CoroutinePipelineExecutor` read `gate.faultTolerant` without a `when` over gate types, and `VisualPipelineModel` (which holds `Gate` by reference and copies via `.copy()`) preserves it for free. Alternative considered: a separate `Set<GateId>` on `Pipeline`. Rejected — it splits gate config away from the gate, complicates YAML (the flag is naturally a per-gate field), and breaks the existing `.copy(id = ...)` rebuild path in the visual model.

**Decision:** Intercept the failure in `CoroutinePipelineExecutor.handleResult`. When `result is GateResult.Failure` and `gate.faultTolerant` is true, emit a `TOLERATED_FAILURE` trace entry and return the failure-continuation gate id (do not throw). When `gate.faultTolerant` is false, behavior is unchanged (throw `result.error`).
**Reason:** This is the single choke point where the engine decides whether to abort. FR-3 (fail-fast unchanged), FR-4 (continue), and FR-5 (no outputs applied) all collapse to behavior at this one site: the existing `GateResult.Failure` branch already does *not* call `applyAndTrace`, so a failed gate already contributes no outputs — FR-5/FR-6 are satisfied with no extra work. We only change what happens *after* the trace entry: continue instead of throw.

**Decision:** The failure-continuation port for a tolerated failure is `gate.defaultPort` for a `LogicGate`, and `"out"` for every other gate type.
**Reason:** The single-path executor cannot fork, so it must pick exactly one outgoing port. Normal gates leave via the `"out"` port. `LogicGate` is special: it has no `"out"` edges — it routes via branch ports. Per `DefaultLogicGateExecutor`, a logic gate only returns `GateResult.Failure` in one case: no branch matched *and* `defaultPort == null` (a branch evaluating false is `Routed`, i.e. success — FR-11). In that failure case `defaultPort` is null, so `nextGateId(gate.id, null-port)` yields no edge and the run ends after recording the tolerated failure — which is exactly the spec's "fault-tolerant gate with no outgoing edge: record and end normally" edge case. Using `defaultPort` (falling back to `"out"`) is the correct, type-aware rule rather than blindly using `"out"`, which would silently never continue for any logic gate. This rule is documented so the developer does not guess.

**Decision:** Add `TraceStatus.TOLERATED_FAILURE` (domain) and `GateStatus.TOLERATED_FAILURE` (ui), rendered with a distinct icon and label, and explicitly map `TraceStatus.TOLERATED_FAILURE -> GateStatus.TOLERATED_FAILURE` in `FlaiPipelineUiService.handleEvent`.
**Reason:** FR-8/FR-9/AC-6/AC-7 require a tolerated failure to be recorded distinctly and *never* shown as success. The existing `when (entry.status)` in `handleEvent` has an `else -> GateStatus.SUCCESS` fallback; without an explicit branch a new status would silently fall into it and be displayed as success — a direct FR-9 violation. The renderer's `when (row.status)` over the `GateStatus` enum is exhaustive, so adding a `GateStatus` value forces a compile error there (guaranteeing we render it), but the `handleEvent` mapping does NOT force one — so it must be fixed by hand. Reusing `FAILURE` was rejected because the trace must distinguish "aborted the run" from "tolerated and continued" (AC-6: the entry must indicate the pipeline continued because the gate was fault tolerant).

**Decision:** Keep the tolerated-failure trace `message` informative — prefix the failure reason to make the "continued because fault tolerant" intent explicit, e.g. `"tolerated failure: <reason>"`, falling back to a generic reason when none is available.
**Reason:** AC-6 requires the entry to state the failure reason and that the pipeline continued; the edge case "fails with no recoverable reason" (still recorded as tolerated, never success) is covered by the fallback string.

## Data Model

`Gate` base class gains an abstract property; each subclass gains a defaulted constructor parameter (shown for two representative gates; applies to all eight):

```kotlin
sealed class Gate {
    abstract val id: GateId
    abstract val label: String
    abstract val faultTolerant: Boolean
}

data class InputGate(
    override val id: GateId,
    override val label: String,
    val inputSchema: List<InputField> = emptyList(),
    override val faultTolerant: Boolean = false,
) : Gate()

data class BashGate(
    override val id: GateId,
    override val label: String,
    val command: String,
    val workingDirectory: String = ".",
    val environment: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 120,
    val failOnNonZeroExit: Boolean = true,
    val outputMapping: Map<String, String> = emptyMap(),
    override val faultTolerant: Boolean = false,
) : Gate()
```

`override val faultTolerant: Boolean = false` is added as the **last** constructor parameter of all eight gates: `InputGate`, `OutputGate`, `LlmGate`, `LogicGate`, `ToolGate`, `BashGate`, `ReadFileGate`, `WriteFileGate`. Appending it (with a default) keeps every existing positional and named construction site compiling unchanged.

New trace status:

```kotlin
enum class TraceStatus { STARTED, SUCCESS, FAILURE, SKIPPED, TOLERATED_FAILURE }
```

New UI gate status:

```kotlin
enum class GateStatus { RUNNING, SUCCESS, FAILURE, OUTPUT, TOLERATED_FAILURE }
```

## APIs / Interfaces

No public interface signatures change. `PipelineExecutor.execute`, `GateExecutor`, and `ExecutionEvent` are untouched — a tolerated failure is surfaced through the existing `ExecutionEvent.GateCompleted(TraceEntry)` channel, with the new `TraceStatus.TOLERATED_FAILURE` on the entry.

The internal `handleResult` failure branch changes shape:

```kotlin
is GateResult.Failure -> {
    if (gate.faultTolerant) {
        val reason = result.message
        val entry = TraceEntry(
            gate.id, gate.label, TraceStatus.TOLERATED_FAILURE,
            "tolerated failure: $reason", duration,
        )
        context.trace += entry
        send(ExecutionEvent.GateCompleted(entry))
        // type-aware continuation port: logic gates route via defaultPort, others via "out"
        val port = if (gate is LogicGate) gate.defaultPort ?: "out" else "out"
        pipeline.nextGateId(gate.id, port)
    } else {
        val entry = TraceEntry(gate.id, gate.label, TraceStatus.FAILURE, result.message, duration)
        context.trace += entry
        send(ExecutionEvent.GateCompleted(entry))
        throw result.error
    }
}
```

YAML schema gains one optional per-gate key, valid on every gate type:

```yaml
some-gate:
  type: bash
  label: Optional best-effort step
  command: ./flaky.sh
  faultTolerant: true   # optional, default false
```

## File-Level Plan

- `src/main/kotlin/me/drew/flai/domain/model/Gate.kt` — modify: add abstract `faultTolerant: Boolean` to `Gate`; add `override val faultTolerant: Boolean = false` as the last param of all eight gate data classes.
- `src/main/kotlin/me/drew/flai/domain/model/ExecutionContext.kt` — modify: add `TOLERATED_FAILURE` to the `TraceStatus` enum.
- `src/main/kotlin/me/drew/flai/infrastructure/executor/CoroutinePipelineExecutor.kt` — modify: in `handleResult`, split the `GateResult.Failure` branch into fault-tolerant (record `TOLERATED_FAILURE`, continue via `defaultPort`/`"out"`) vs. fail-fast (existing throw) paths.
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParser.kt` — modify: in `parseGate`, read `faultTolerant` once at the top (like `label`) via a `parseBoolean(id, map["faultTolerant"], "faultTolerant") ?: false` helper call, and pass it into all eight gate constructors.
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializer.kt` — modify: in `appendGate`, emit `faultTolerant: true` once (only when true, following the existing "write only non-default" pattern). Emit it in a single shared spot after `label`, not per-branch.
- `src/main/kotlin/me/drew/flai/ui/model/UiModels.kt` — modify: add `TOLERATED_FAILURE` to the `GateStatus` enum.
- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt` — modify: in `handleEvent`, add explicit `TraceStatus.TOLERATED_FAILURE -> GateStatus.TOLERATED_FAILURE` mapping so it cannot fall through `else -> SUCCESS`. Confirm the `PipelineFailed` guard (`none { it.status == GateStatus.FAILURE }`) is unaffected — a tolerated failure is not `FAILURE`, so it correctly does not suppress a later genuine failure row.
- `src/main/kotlin/me/drew/flai/ui/toolwindow/ExecutionLogPanel.kt` — modify: add a `GateStatus.TOLERATED_FAILURE` branch to the renderer's `when` (distinct icon, e.g. `AllIcons.General.Warning` or `BalloonWarning`, with warning-colored text and the message), so it is visually distinct from both SUCCESS and FAILURE.
- `docs/pipeline-yaml-spec.md` — modify: document the `faultTolerant` per-gate field, its default (off), that it applies to all gate types, and its runtime behavior (tolerated failure recorded distinctly, no outputs contributed, continues along the gate's outgoing edge; for `logic` gates, along `defaultPort`).
- `src/test/kotlin/me/drew/flai/infrastructure/executor/CoroutinePipelineExecutorFaultTolerantTest.kt` — create: executor-level behavior tests (see Test Strategy).
- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParserFaultTolerantTest.kt` — create: parse/default tests.
- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineSerializerTest.kt` — modify: add round-trip assertions for `faultTolerant`.

## Implementation Order
Each step is independently committable and leaves the build green.

1. **Domain model.** Add `faultTolerant` to `Gate` + subclasses; add `TraceStatus.TOLERATED_FAILURE`. Build compiles (defaults keep all call sites valid).
2. **Executor behavior.** Split the `Failure` branch in `CoroutinePipelineExecutor.handleResult`. Add `CoroutinePipelineExecutorFaultTolerantTest`. This is the core behavioral commit.
3. **YAML parse.** Read `faultTolerant` in `YamlPipelineParser.parseGate`. Add parser tests (present=true, present=false, absent=false default, non-boolean=error).
4. **YAML serialize + round-trip.** Emit the flag in `YamlPipelineSerializer`; extend serializer test to assert parse→serialize→parse preserves `faultTolerant` (this also guards the visual-editor save path).
5. **UI surfacing.** Add `GateStatus.TOLERATED_FAILURE`, the `handleEvent` mapping, and the renderer branch. Add a mapping unit test if practical.
6. **Docs.** Update `docs/pipeline-yaml-spec.md`.

## Test Strategy

**Unit (executor) — `CoroutinePipelineExecutorFaultTolerantTest`** — the primary coverage, using fake `GateExecutor`s that return `GateResult.Failure` (the existing tests already use this pattern):
- AC-2: non-fault-tolerant gate fails → flow emits `PipelineFailed`, downstream gate's `GateStarted` is never emitted.
- AC-3: fault-tolerant gate fails → no `PipelineFailed`; downstream gate (along `"out"`) emits `GateStarted`/`GateCompleted`; flow ends with `PipelineCompleted`.
- AC-4: after a tolerated failure, assert the downstream gate sees the pre-failure context (the failed gate's would-be output key is absent).
- AC-5: parametrize over a fault-tolerant gate of each type returning `Failure`; assert continuation. For `logic`, use a gate with `defaultPort = null` and a failing condition set → assert tolerated-failure entry recorded and run ends normally (no `"out"` edge exists), and separately a logic gate with a `defaultPort` that fails → continues along `defaultPort`.
- AC-6/AC-9 / FR-11: assert the emitted `TraceEntry` has `TraceStatus.TOLERATED_FAILURE`, a message containing the reason; and that a logic gate evaluating a false branch is recorded `SUCCESS` (Routed), unaffected by the flag.
- Edge cases: entry gate is fault tolerant and fails (continues along its out edge); fault-tolerant gate with no outgoing edge (run ends normally); two fault-tolerant gates fail in one run (two distinct `TOLERATED_FAILURE` entries); a non-fault-tolerant gate downstream of a tolerated failure fails → `PipelineFailed` (fail-fast still applies); fault-tolerant gate with `Failure(message = "")`/null reason still recorded as tolerated, never success.

**Unit (parser/serializer):**
- Parser: `faultTolerant: true`, `false`, absent (defaults false), and non-boolean (raises `PipelineLoadException`).
- Serializer round-trip: parse → serialize → parse preserves `faultTolerant` for at least one gate; a `false`/absent gate does not emit the key.

**Unit (UI mapping):** verify `TraceStatus.TOLERATED_FAILURE` maps to `GateStatus.TOLERATED_FAILURE` (guards AC-7 — never `SUCCESS`). The renderer branch is covered implicitly by the exhaustive `when`.

**Backward compatibility (NFR-1 / AC-8):** existing executor and serializer tests must pass unchanged — defaults guarantee no behavioral change for gates without the flag.

## Risks

- **Silent FR-9 violation via the `else -> SUCCESS` fallback in `FlaiPipelineUiService.handleEvent`.** A new `TraceStatus` will compile fine while falling through to `SUCCESS`, presenting a tolerated failure as success. Mitigation: the explicit mapping branch in step 5 is mandatory; the UI mapping unit test guards it. (The renderer's exhaustive `GateStatus` `when` is safe — it will fail to compile until handled.)
- **Logic-gate continuation.** Using `"out"` blindly would silently never continue for logic gates. Mitigated by the `defaultPort`-aware rule, documented in Tech Decisions and covered by AC-5 logic tests.
- **Visual-editor save dropping the flag.** `VisualPipelineModel` round-trips `Gate` by reference and rebuilds via `.copy(id = ...)`, both of which preserve a new data-class field automatically. Low risk, but the serializer round-trip test (step 4) explicitly guards it so an editor open/save cannot silently strip `faultTolerant`.
- **Constructor parameter ordering.** Adding `faultTolerant` anywhere but last could break positional construction sites. Mitigated by always appending it as the final defaulted parameter.

## Questions for Business Analyst
_None_
