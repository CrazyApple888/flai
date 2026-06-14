# Functional Spec: Fault-Tolerant Gate

## Functional Requirements
- FR-1: A pipeline author can mark an individual gate as fault tolerant via a per-gate, opt-in flag (author-facing name: `faultTolerant`).
- FR-2: The `faultTolerant` flag is disabled by default. A gate with no flag set behaves identically to its current behavior.
- FR-3: When a gate without `faultTolerant` enabled fails, the pipeline aborts the run exactly as it does today (fail-fast).
- FR-4: When a gate with `faultTolerant` enabled fails, the pipeline does not abort. It records the failure and continues execution along that gate's normal outgoing edges, as if the gate had completed.
- FR-5: A failed fault-tolerant gate contributes no outputs to the execution context. The context is left in whatever state existed before the gate ran; any values the gate would have produced remain absent.
- FR-6: Downstream gates execute against the existing execution context regardless of the fault-tolerant gate's failure. The system does not synthesize, default, or recover the missing outputs.
- FR-7: The `faultTolerant` flag applies uniformly to every gate type: `input`, `llm`, `logic`, `tool`, `bash`, `read-file`, `write-file`, and `output`.
- FR-8: A tolerated failure is recorded distinctly from a success. The execution log and run results must show the failed gate's identity, that the gate failed, the reason for failure, and that the pipeline continued because the gate was fault tolerant.
- FR-9: A tolerated failure must never be presented or recorded as a successful gate execution.
- FR-10: The behavior of successful gates and of non-fault-tolerant gates is unchanged by this feature.
- FR-11: For the purpose of this feature, a gate "failure" means an execution error during the gate's run. A `logic` gate evaluating to a false/negative branch is normal successful execution, not a failure, and is unaffected by this flag.
- FR-12: The pipeline YAML spec documentation is updated to describe the flag, its default (off), and its runtime behavior.

## User Stories
- As a pipeline author, I want to mark a non-critical gate (e.g. a flaky shell command, an optional file read, a best-effort LLM call) as fault tolerant, so that its failure does not abort the entire run.
- As a pipeline author, I want gates I have not flagged to keep failing the run on error, so that critical steps still enforce fail-fast behavior.
- As a pipeline author with existing pipelines, I want my pipelines to behave exactly as before without any changes, so that adopting the new plugin version is safe.
- As a pipeline operator reviewing a run, I want to see clearly in the execution log that a specific gate failed, why it failed, and that the pipeline deliberately continued because the gate was fault tolerant, so that I never mistake a tolerated failure for a success.

## Acceptance Criteria
- AC-1 (FR-1, FR-2): Given a gate without the `faultTolerant` flag, when the pipeline is authored and run, then the gate is treated as not fault tolerant.
- AC-2 (FR-3): Given a non-fault-tolerant gate that fails at runtime, when it fails, then the pipeline run aborts and no further gates execute (matching current behavior).
- AC-3 (FR-4): Given a fault-tolerant gate that fails at runtime, when it fails, then the run does not abort and execution proceeds along that gate's defined outgoing edges.
- AC-4 (FR-5, FR-6): Given a fault-tolerant gate fails, when a downstream gate runs, then the context contains no outputs from the failed gate and the downstream gate executes against the pre-failure context.
- AC-5 (FR-7): For each of `input`, `llm`, `logic`, `tool`, `bash`, `read-file`, `write-file`, and `output`, when an instance of that gate type is marked fault tolerant and fails, then the pipeline continues per FR-4.
- AC-6 (FR-8): Given a fault-tolerant gate fails, when the run completes, then the execution log/results contain an entry for that gate identifying it, marking it as failed (not succeeded), stating the failure reason, and indicating the pipeline continued because the gate was fault tolerant.
- AC-7 (FR-9): Given a fault-tolerant gate fails, when results are inspected, then no view, summary, or status presents that gate as having succeeded.
- AC-8 (FR-10): Given a pipeline of successful and/or non-fault-tolerant gates, when run, then results are identical to behavior before this feature.
- AC-9 (FR-11): Given a `logic` gate that evaluates to a false/negative branch (no execution error), when it runs, then it is recorded as a successful gate and the `faultTolerant` flag has no effect on its handling.

## Edge Cases & Error Scenarios
- The entry gate is fault tolerant and fails: the run does not abort; execution continues along the entry gate's outgoing edges (if any).
- A fault-tolerant gate that fails has no outgoing edges: the failure is recorded and the run ends normally with no further gates to execute.
- A downstream gate references a context value the failed fault-tolerant gate would have set: that value is absent; the downstream gate runs against the existing context (responsibility for tolerating the missing value lies with the author, per FR-6).
- Multiple fault-tolerant gates fail within a single run: each failure is recorded distinctly per FR-8, and the run continues past each one along its respective outgoing edges.
- A fault-tolerant gate that feeds the `output` gate fails: the `output` gate runs against the existing context, with the failed gate's outputs absent.
- A non-fault-tolerant gate fails downstream of a tolerated failure: standard fail-fast applies and the run aborts at that point (the earlier tolerated failure does not change this).
- A fault-tolerant gate fails with no recoverable error reason available: the failure is still recorded as a tolerated failure (FR-8); absence of a detailed reason must not cause it to be recorded as success.

## Non-Functional Requirements
- NFR-1 (Backward compatibility): Existing pipelines that do not use the `faultTolerant` flag must behave exactly as before this feature was introduced. The flag's default-off state guarantees no behavioral change for current pipelines.

## Out of Scope
- Retry logic: a fault-tolerant gate is run once and is not re-attempted on failure.
- Fallback or alternate-branch routing on failure: routing to a different path based on failure is a `logic` gate concern, not this flag.
- Partial-output recovery: the feature does not salvage or synthesize the outputs a failed gate would have produced.
- A pipeline-wide "ignore all errors" setting: the flag is strictly per-gate and opt-in.
- Changes to the behavior of successful gates or non-fault-tolerant gates.
- Exposing the flag in the per-gate visual config panel: YAML is the confirmed primary authoring surface; panel support is deferred and can be scoped separately.
- Writing a failure status/marker value into the execution context for later `logic` gates to branch on: the confirmed default is that a failed fault-tolerant gate contributes no outputs and downstream gates proceed against the existing context. Adding a branchable failure marker is deferred (it overlaps with the failure-routing Non-Goal owned by `logic` gates) and is not part of this feature.

## Questions for PM
_None_
