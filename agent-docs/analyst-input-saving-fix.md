# Functional Spec: Input Field Value Persistence Fix

## Functional Requirements

- FR-1: When the user types values into input gate form fields and clicks Run, the pipeline executor must receive those typed values — not empty strings, nulls, or defaults.
- FR-2: The system must retain, in memory for the duration of the IDE session, the last-typed values for each input field, keyed per pipeline.
- FR-3: Retained values must be restored to the form fields when the user switches to a different pipeline and returns to the original pipeline within the same IDE session.
- FR-4: Retained values must be restored to the form fields when the user closes and reopens the Flai Pipelines tool window within the same IDE session.
- FR-5: Retained values must be restored to the form fields when the user runs the same pipeline a second time without navigating away.
- FR-6: The retention and forwarding behavior must apply to all four schema field types: STRING, NUMBER, BOOLEAN, and JSON.
- FR-7: Retained values must be isolated per pipeline — values entered for pipeline A must not appear in any field belonging to pipeline B.
- FR-8: If a pipeline has multiple input gates, each gate's fields must be retained and restored independently.
- FR-9: If the user's previously retained value corresponds to a field that no longer exists in the pipeline YAML (e.g., the field was renamed or removed), that stale value must be silently discarded and the field must appear empty.
- FR-10: The fix must introduce no validation logic beyond what already exists for input fields — invalid inputs are handled exactly as they are today.

## User Stories

**US-1: Developer runs a pipeline with input fields**
As a developer, I want the values I type into input gate fields to be forwarded to the pipeline when I click Run, so that the pipeline executes with my supplied data rather than empty or null values.

**US-2: Developer re-runs the same pipeline**
As a developer, I want my previously typed input values to remain populated in the form after a run completes, so that I can run the same pipeline again without retyping all inputs.

**US-3: Developer switches between pipelines and returns**
As a developer, I want my typed input values to be preserved when I navigate away to another pipeline and come back, so that context switching does not destroy work I have already done.

**US-4: Developer reopens the tool window**
As a developer, I want my typed input values to survive closing and reopening the Flai Pipelines tool window during the same IDE session, so that accidental window closure does not require re-entering all inputs.

**US-5: Developer works with all supported field types**
As a developer, I want value retention and forwarding to work correctly for STRING, NUMBER, BOOLEAN, and JSON field types, so that any pipeline I build is supported regardless of the data types it requires.

## Acceptance Criteria

**AC for FR-1 / US-1**
- AC-1.1: Given a pipeline with an input gate containing at least one field, when the user types a value and clicks Run, the `ExecutionContext` supplied to the pipeline executor contains that exact typed value for that field key.
- AC-1.2: If the user has not typed anything into a field, the executor receives whatever the current behavior supplies (empty string, null, or schema default) — the fix does not alter this baseline.

**AC for FR-2 through FR-5 / US-2 through US-4**
- AC-2.1: After the user types values and clicks Run, the form fields still display those values immediately after the run completes.
- AC-2.2: After the user selects a different pipeline and then reselects the original pipeline, the input fields display the values the user last typed for that pipeline.
- AC-2.3: After the user closes the Flai Pipelines tool window and reopens it within the same IDE session, the input fields for the previously used pipeline display the values last typed.
- AC-2.4: Retained values are cleared when the IDE process exits — no persistence to disk.
- AC-2.5: Retained values are discarded when the project is closed, even if the IDE process remains running.

**AC for FR-6 / US-5**
- AC-6.1: A STRING field value typed by the user is retained and forwarded as a string.
- AC-6.2: A NUMBER field value typed by the user is retained and forwarded as entered; no additional type coercion is introduced by this fix.
- AC-6.3: A BOOLEAN field value set by the user is retained and forwarded correctly.
- AC-6.4: A JSON field value typed by the user is retained and forwarded as entered; if the content is invalid JSON, behavior is identical to the current behavior (no new validation added).

**AC for FR-7 / US-3**
- AC-7.1: Entering and saving values for pipeline A has no effect on the field values displayed for pipeline B.

**AC for FR-8**
- AC-8.1: A pipeline with two input gates retains and restores field values for each gate independently and correctly.

**AC for FR-9**
- AC-9.1: If a field key was previously retained but is absent from the pipeline's current schema, the field is rendered empty and no error is shown.

**AC for FR-10**
- AC-10.1: No new validation errors or warnings are introduced for any field type by this fix.

## Edge Cases & Error Scenarios

- **Empty field on first run:** User has never typed into a field — the executor receives the same value it would have received before this fix (no regression).
- **Multiple input gates in one pipeline:** Each gate contributes its own field set; all are retained and restored independently (FR-8).
- **Schema drift — field removed:** A field present in retained state is no longer in the YAML; stale value is silently discarded, field renders empty (FR-9).
- **Schema drift — field renamed:** Old name is treated as removed (discarded), new name has no retained value and renders empty.
- **Invalid JSON in a JSON field:** No new validation introduced; field value is passed to executor as-is, same as today (FR-10, AC-6.4).
- **Pipeline with no input gates:** No form fields are shown; retention mechanism is not invoked. Behavior is unchanged.
- **Gutter icon run trigger:** Run is triggered from the editor gutter icon rather than the tool window Run button; the same retained values for that pipeline must be forwarded to the executor (FR-1 applies regardless of trigger source).
- **Tool window rebuilt by the IDE:** If the IntelliJ platform recreates the tool window component, retained values must survive because they are stored outside the UI component lifecycle (FR-4).
- **Concurrent runs:** If the platform allows a second run to be triggered while one is in progress, the retained values at the time of each trigger are used; no race condition should corrupt the retained state.

## Non-Functional Requirements

- NFR-1: Retained input values are stored in memory only (no disk write, no `PasswordSafe`, no persistent state file) — data does not outlive the IDE process.
- NFR-2: The retention lookup and write must not introduce perceptible latency to the Run action or to pipeline selection switching.

## Out of Scope

- Persisting input values across IDE restarts or across project close/reopen.
- Input validation beyond what already exists (required-field enforcement, type coercion, format checks).
- Changes to the pipeline YAML schema or the `input` gate definition.
- Changes to how `ExecutionContext` processes or propagates values once correctly supplied.
- UI redesign of the input form fields — visual appearance is unchanged.
- Adding new field types to the input gate schema.

## Questions for PM

_None_
