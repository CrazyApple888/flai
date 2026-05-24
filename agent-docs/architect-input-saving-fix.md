# Architecture: Input Field Value Persistence Fix

## Overview

This is a bug fix spanning the UI layer and the gutter-run path. Three distinct failures cause input values to be lost or ignored:

1. **State lifetime mismatch** — `PipelineDetailPanel.inputValues` is a field on a Swing component. IntelliJ may recreate the tool window component at any time, discarding that map and its typed values.
2. **`showPipeline` always resets** — every call to `PipelineDetailPanel.showPipeline()` does `inputValues.clear()` followed by restoration from `spec.defaultValue`. Switching pipelines and returning, or any other code path that re-calls `showPipeline`, silently overwrites what the user typed.
3. **Gutter-run ignores typed values** — `FlaiPipelineUiService.runFromFile()` builds the inputs map exclusively from `spec.defaultValue`, bypassing the UI input map entirely.

Layers touched: `ui/service/FlaiPipelineUiService`, `ui/toolwindow/PipelineDetailPanel`. No domain or infrastructure changes are required.

## Tech Decisions

**Decision:** The per-pipeline input value store is a `ConcurrentHashMap<PipelineId, Map<String, String>>` owned by `FlaiPipelineUiService`.
**Reason:** `FlaiPipelineUiService` is a project-level IntelliJ `@Service` that lives for the lifetime of the project, which satisfies AC-2.3 (tool window close/reopen), AC-2.4 (cleared on IDE exit), and AC-2.5 (cleared on project close) for free, without any disk persistence. Ownership in the service also makes the store available to both the tool-window Run path and the gutter-run path via a single API.

**Decision:** BOOLEAN and other non-string field types are retained and forwarded as strings through the existing `JBTextField` round-trip.
**Reason:** The PM doc and functional spec explicitly place UI redesign out of scope. All four field types (`STRING`, `NUMBER`, `BOOLEAN`, `JSON`) are rendered as `JBTextField` today. Retaining and forwarding the raw text string is consistent with AC-6.1–6.4, which explicitly say "as entered / as typed / identical to current behavior." The existing `DefaultInputGateExecutor` and downstream gates are already responsible for type interpretation.

**Decision:** The retained map is keyed by `PipelineId`, with field key as the inner key.
**Reason:** FR-7 requires per-pipeline isolation. `PipelineId` is the stable identifier already used throughout the domain and UI models.

**Decision:** FR-8 (multiple input gates per pipeline) is satisfied trivially by the existing single-entry-gate behavior; no multi-gate expansion is included in this fix.
**Reason:** `toUiPipelineFromFile` currently reads only `pipeline.gates[pipeline.entryGateId] as? InputGate`. A pipeline's `UiPipeline.inputSpecs` therefore contains fields from at most one gate. FR-8's requirement — that each gate's fields be retained and restored independently — is met for the single gate that today's UI exposes. Broadening `toUiPipelineFromFile` to walk all input gates is a pre-existing scope gap outside the PM doc's narrow boundary ("Changes to how `ExecutionContext` processes or propagates values once they have been correctly supplied" is explicitly out of scope, and the UI rendering of multi-gate inputs is in the same category). A developer must not silently add multi-gate support here.

**Decision:** The EDT-side `DocumentListener` in `PipelineDetailPanel` writes directly to the service's store via a new `saveInputValues(PipelineId, Map<String, String>)` method, replacing the current write to the local `inputValues` map.
**Reason:** This eliminates the local map as the source of truth. The panel's local `inputValues` variable is kept only as a short-lived convenience buffer during `rebuildInputs`, but the service store is the authoritative source. This keeps concurrency simple: the local buffer is only read/written on the EDT; the service map is written from the EDT and read from `serviceScope` (Dispatchers.Default), so `ConcurrentHashMap` is sufficient.

**Decision:** `showPipeline` populates the local buffer by merging the retained map over defaults, then prunes stale keys.
**Reason:** FR-9 requires that field keys absent from the current `inputSpecs` are silently discarded. The merge-then-prune approach ensures that: (a) retained values are used when present, (b) defaults are used as fallback for new/unseen fields, (c) removed field keys from previous YAML versions are not forwarded to the executor.

**Decision:** `runFromFile` is updated to merge retained values over defaults before calling `run()`.
**Reason:** The gutter-run edge case is explicitly called out in the functional spec (edge cases section). Without this fix, gutter runs always ignore user-typed values. The fix makes both Run paths (tool window button and gutter icon) use the same source of truth.

## Data Model

No new persistent data structures. One new in-memory store in `FlaiPipelineUiService`:

```kotlin
// In FlaiPipelineUiService
// Key: pipeline id; Value: snapshot of all input field values for that pipeline
private val savedInputs: ConcurrentHashMap<PipelineId, Map<String, String>> = ConcurrentHashMap()
```

The inner map is a read-only snapshot (`Map<String, String>`) stored on each save, produced by `inputValues.toMap()` on the EDT. This avoids exposing a mutable reference.

## APIs / Interfaces

Two new methods on `FlaiPipelineUiService` (both called from `PipelineDetailPanel` or `runFromFile`):

```kotlin
/**
 * Persist the current input field values for a pipeline.
 * Replaces any previously saved values for this pipeline.
 * Safe to call from any thread; [values] is copied on store.
 */
fun saveInputValues(pipelineId: PipelineId, values: Map<String, String>) {
    savedInputs[pipelineId] = values.toMap()
}

/**
 * Return the last saved input values for a pipeline, or an empty map
 * if no values have been saved yet.
 */
fun getSavedInputValues(pipelineId: PipelineId): Map<String, String> =
    savedInputs[pipelineId] ?: emptyMap()
```

Updated `showPipeline` merge logic in `PipelineDetailPanel`:

```kotlin
fun showPipeline(pipeline: UiPipeline) {
    currentPipeline = pipeline
    val retained = service.getSavedInputValues(pipeline.id)
    inputValues.clear()
    // Populate only keys present in current schema; retained overrides default.
    // Keys in retained that are absent from current inputSpecs are implicitly
    // discarded by this loop (FR-9). Write the pruned map back to the store so
    // that stale keys are removed even if the user does not type anything.
    pipeline.inputSpecs.forEach { spec ->
        inputValues[spec.key] = retained[spec.key] ?: spec.defaultValue
    }
    service.saveInputValues(pipeline.id, inputValues.toMap())
    rebuildInputs(pipeline)
}
```

Note on AC-1.2: the inputs map always contains every schema key with at least the spec default, matching the shape produced by the current code. No regression for fields the user has never typed into.

Updated `DocumentListener` write-through in `rebuildInputs`:

```kotlin
private fun update() {
    inputValues[spec.key] = textField.text
    val p = currentPipeline ?: return
    service.saveInputValues(p.id, inputValues.toMap())
}
```

Updated `runFromFile` in `FlaiPipelineUiService`:

```kotlin
fun runFromFile(filePath: String) {
    serviceScope.launch {
        var pipeline = _pipelines.value.firstOrNull { it.filePath?.toString() == filePath }
        if (pipeline == null) {
            pipeline = runCatching { toUiPipelineFromFile(File(filePath)) }.getOrNull()
        }
        pipeline ?: return@launch
        if (_pipelines.value.none { it.id == pipeline.id }) {
            _pipelines.value = _pipelines.value + pipeline
        }
        // Build inputs: retained values take precedence over spec defaults
        val retained = getSavedInputValues(pipeline.id)
        val inputs = pipeline.inputSpecs.associate { spec ->
            spec.key to (retained[spec.key] ?: spec.defaultValue)
        }
        run(pipeline, inputs)
    }
}
```

## File-Level Plan

- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt` — modify: add `savedInputs: ConcurrentHashMap<PipelineId, Map<String, String>>` field; add `saveInputValues()` and `getSavedInputValues()` methods; update `runFromFile()` to merge retained values over defaults.
- `src/main/kotlin/me/drew/flai/ui/toolwindow/PipelineDetailPanel.kt` — modify: update `showPipeline()` to read from `service.getSavedInputValues()` instead of clearing to defaults; update the `DocumentListener.update()` in `rebuildInputs()` to call `service.saveInputValues()` after writing to the local buffer.

No other files require changes.

## Implementation Order

1. **Add the in-memory store and its two accessor methods to `FlaiPipelineUiService`** — `savedInputs` field, `saveInputValues()`, `getSavedInputValues()`. No behavior changes yet; existing callers are untouched.

2. **Fix `showPipeline` in `PipelineDetailPanel`** — replace `inputValues.clear()` + default-fill with the merge-then-prune logic using `service.getSavedInputValues()`. After this step, the panel correctly restores retained values when switching pipelines or when the tool window is rebuilt.

3. **Fix the `DocumentListener` write-through in `rebuildInputs`** — after updating the local buffer, call `service.saveInputValues(currentPipeline.id, inputValues.toMap())`. After this step, every keystroke is persisted to the service store, making it available for step 4 and for tool-window rebuild scenarios.

4. **Fix `runFromFile` in `FlaiPipelineUiService`** — replace the default-only inputs map with the merge of retained values over defaults. After this step, gutter-icon runs use the same saved values as tool-window runs.

Each step is independently committable and does not break the others when applied in order.

## Test Strategy

All tests are unit tests (no IntelliJ platform required for the logic; the service can be tested with a minimal mock or by extracting the merge logic into a pure function).

**Unit — merge logic (pure function, no IDE dep):**
- Given retained `{"key": "typed"}` and spec default `"default"`, result is `"typed"`.
- Given no retained entry and spec default `"default"`, result is `"default"`.
- Given retained contains a key not in current `inputSpecs`, that key is absent from the result (FR-9).
- Given retained map is empty, all fields use their spec defaults.

**Unit — `saveInputValues` / `getSavedInputValues` round-trip:**
- Save a map for pipeline A; retrieve for pipeline A returns the same values.
- Save for pipeline A; retrieve for pipeline B returns empty map (FR-7 isolation).
- Save twice for same pipeline; second save wins.

**Unit — `runFromFile` input construction:**
- Pipeline has two fields; one has a retained value, one does not. Inputs map passed to `run()` contains the retained value for the first field and the default for the second.
- Pipeline has no retained values; inputs map equals the spec-default map (no regression, AC-1.2).

**Integration (manual / IDE sandbox):**
- Type values into all four field types (STRING, NUMBER, BOOLEAN, JSON), click Run — verify execution context receives typed values (AC-1.1).
- Run, switch to another pipeline, switch back — fields still show typed values (AC-2.2 / FR-3).
- Close and reopen the Flai Pipelines tool window — fields still show typed values (AC-2.3 / FR-4).
- Trigger run via gutter icon after typing values in tool window — pipeline receives typed values, not defaults (edge case from functional spec).
- Pipeline A values do not appear in pipeline B fields (AC-7.1 / FR-7).
- Remove a field from the YAML, reload — that field's retained value is discarded, remaining fields retain their values (FR-9).

## Risks

**Risk:** EDT/background thread access to `savedInputs`. The `DocumentListener` writes from the EDT; `runFromFile` reads from `Dispatchers.Default`.
**Mitigation:** Use `ConcurrentHashMap` for `savedInputs`, and store only immutable `Map<String, String>` snapshots as values (produced via `toMap()`). Reads from `Dispatchers.Default` get a consistent snapshot. This is the same pattern already used in `ExecutionContext`.

**Risk:** `showPipeline` being called before the service store is populated on first use.
**Mitigation:** `getSavedInputValues` returns `emptyMap()` when nothing has been saved, causing the merge to fall back entirely to spec defaults — identical to today's baseline behavior (AC-1.2).

**Risk:** `runFromFile` calling `run()` with the wrong pipeline reference if `_pipelines` is updated concurrently.
**Mitigation:** This risk pre-exists and is unchanged by this fix. `savedInputs` lookup is done after the pipeline reference is resolved, so no new race is introduced.

## Questions for Business Analyst

_None_
