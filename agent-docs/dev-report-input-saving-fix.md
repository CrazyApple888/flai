# Dev Report: Input Field Value Persistence Fix

## Implemented

Fixed three distinct bugs that caused input gate field values to be lost or ignored when running pipelines.

### Files modified

**`src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt`**
- Added `import java.util.concurrent.ConcurrentHashMap`.
- Added top-level `internal fun mergeInputs(specs, retained)` pure function — merges retained values over spec defaults; keys absent from `specs` are implicitly discarded (implements FR-9 stale-key pruning). Being a top-level function allows unit testing without an IntelliJ `Project`.
- Added `private val savedInputs: ConcurrentHashMap<PipelineId, Map<String, String>>` field.
- Added `fun saveInputValues(pipelineId, values)` — stores an immutable snapshot of values.
- Added `fun getSavedInputValues(pipelineId)` — returns saved map or `emptyMap()` on first use.
- Fixed `runFromFile()` — replaced the old `filter { defaultValue.isNotEmpty() }.associate { ... }` (which ignored retained values and skipped empty-default fields) with `mergeInputs(pipeline.inputSpecs, getSavedInputValues(pipeline.id))`.

**`src/main/kotlin/me/drew/flai/ui/toolwindow/PipelineDetailPanel.kt`**
- Fixed `showPipeline()` — replaced `inputValues.clear(); pipeline.inputSpecs.forEach { inputValues[it.key] = it.defaultValue }` with merge-then-prune logic: fetches retained map from `service.getSavedInputValues()`, populates local buffer preferring retained over default, then immediately writes the pruned snapshot back via `service.saveInputValues()` (removes stale keys even if user never types, FR-9).
- Fixed `DocumentListener.update()` inside `rebuildInputs()` — added `service.saveInputValues(p.id, inputValues.toMap())` call after updating the local buffer, so every keystroke is persisted to the service store.

**`src/test/kotlin/me/drew/flai/infrastructure/executor/DefaultLlmGateExecutorTest.kt`**
- Fixed two `LlmClient` anonymous object implementations that used the old `complete(config, prompt)` signature, updating them to `complete(config, prompt, apiKey: String?)` to match the current interface. This pre-existing breakage blocked `./gradlew test`; fixing it is necessary for the new tests to run.

### Files created

**`src/test/kotlin/me/drew/flai/ui/service/MergeInputsTest.kt`**
- Unit tests for `mergeInputs` pure function: retained overrides default; missing retained falls back to default; stale keys are discarded (FR-9); empty retained uses all defaults; empty specs returns empty map.
- Unit tests for the `ConcurrentHashMap` store semantics: save+retrieve round-trip; per-pipeline isolation (FR-7); second save replaces first; retrieve before save returns empty.
- Unit tests for `runFromFile` input construction scenarios: partial retained (one field retained, other uses default); no retained values produces spec-default map (AC-1.2 regression guard).

## Deviations

None. The architecture doc was followed exactly, including:
- `ConcurrentHashMap<PipelineId, ...>` (not `<String, ...>` as mentioned in the task summary — the architecture doc's type was used).
- Extracting `mergeInputs` as a top-level `internal` function for testability.
- Writing the pruned map back to the store in `showPipeline` (FR-9 stale-key removal even without user typing).
- The `val p = currentPipeline ?: return` guard in `DocumentListener.update()`.
- Removing the `.filter { it.defaultValue.isNotEmpty() }` from `runFromFile` (deliberate, matches tool-window Run path behavior).

## Follow-ups

All unit tests pass (`./gradlew test` — `BUILD SUCCESSFUL`).

Manual integration verification is recommended per the Test Strategy in the architecture doc:
- Type values into all four field types (STRING, NUMBER, BOOLEAN, JSON), click Run — verify execution context receives typed values.
- Run, switch to another pipeline, switch back — fields still show typed values.
- Close and reopen the Flai Pipelines tool window — fields still show typed values.
- Trigger run via gutter icon after typing values in tool window — pipeline receives typed values, not defaults.
- Pipeline A values do not appear in pipeline B fields.
- Remove a field from the YAML, reload — that field's retained value is discarded, remaining fields retain their values.

## Questions for Architect

_None_
