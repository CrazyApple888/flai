# Feature: Input Field Value Persistence Fix

## Slug
input-saving-fix

## Summary
When a user runs a pipeline that has `input` gates, the tool window presents form fields for each input defined in the gate's schema. Currently, values typed into these fields are lost — they are not retained when the user switches pipeline selection, closes and reopens the tool window, or between successive runs of the same pipeline. This bug makes pipelines that require user-provided input (file paths, strings, numbers, JSON) unreliable to use in practice, because the user must retype all values each time. The fix ensures that input field values entered by the user are captured and passed correctly to the pipeline execution, and are retained within the session for repeated runs of the same pipeline.

## Goals
- Values typed into input gate form fields are passed to the pipeline executor when the user triggers a run
- Values typed for a given pipeline are retained in memory for the duration of the IDE session, so re-running the same pipeline does not require retyping inputs
- The fix applies to all schema field types: `STRING`, `NUMBER`, `BOOLEAN`, and `JSON`

## Non-Goals
- Persisting input values across IDE restarts or project close/reopen (session-only retention is sufficient for this fix)
- Validating input field values beyond what is already defined in the gate schema (required fields, type coercion) — that is a separate feature
- Changes to the pipeline YAML schema or the `input` gate definition
- Changes to how the `ExecutionContext` processes or propagates values once they have been correctly supplied
- UI redesign of the input form fields — visual appearance is out of scope

## User-Facing Behavior
1. User opens the **Flai Pipelines** tool window and selects a pipeline that has an `input` gate.
2. The detail panel shows one form field per schema entry defined in the gate (as it does today).
3. User types values into the fields.
4. User clicks **Run** (either from the tool window or the gutter icon).
5. The pipeline executes with the values the user entered — they are not empty, null, or defaulted.
6. The execution log and output reflect the user-supplied values correctly.
7. If the user clicks **Run** again without changing the fields, the same values are used — the fields are still populated with what the user last typed.
8. If the user switches to a different pipeline and back, the previously entered values for the original pipeline are still present in the fields.

## Open Decisions
- None — the scope is narrowly bounded to capturing and forwarding the values the user types, and retaining them per-pipeline within the session.

## Questions
_None_
