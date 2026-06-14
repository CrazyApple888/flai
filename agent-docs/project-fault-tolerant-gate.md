# Feature: Fault-Tolerant Gate

## Slug
fault-tolerant-gate

## Summary
flai pipelines today abort the whole run when any gate fails. This feature adds an opt-in per-gate flag that marks a gate as fault tolerant: when such a gate fails, the pipeline does not stop — execution continues from that gate's normal outgoing edges as if the gate had completed. The flag is disabled by default, so existing pipelines keep their current fail-fast behavior unchanged. This lets pipeline authors build resilient workflows where a single non-critical step (a flaky shell command, an optional file read, a best-effort LLM call) is allowed to fail without sacrificing the rest of the run.

## Goals
- Add a per-gate, opt-in flag that marks a gate as fault tolerant.
- The flag is disabled by default; gates without it keep today's fail-fast behavior.
- When a fault-tolerant gate fails, the pipeline continues executing along that gate's normal outgoing edges instead of aborting.
- The flag applies uniformly across all gate types (`input`, `llm`, `logic`, `tool`, `bash`, `read-file`, `write-file`, `output`).
- A tolerated failure is clearly distinguishable from a success in the execution log and run results — it must never be silently presented as success.
- Document the flag, its default, and its runtime behavior in the pipeline YAML spec.

## Non-Goals
- Not retry logic — a fault-tolerant gate is not re-run on failure; it fails once and the pipeline moves on.
- Not fallback or alternate-branch routing on failure — routing to a different path based on a failure is a `logic` gate concern, not this flag.
- Not partial-output recovery — this feature does not attempt to salvage or synthesize the outputs a failed gate would have produced.
- Not a pipeline-wide "ignore all errors" setting — the flag is scoped to individual gates that explicitly opt in.
- Not a change to how successful gates or non-fault-tolerant gates behave.

## User-Facing Behavior
- A pipeline author can mark an individual gate as fault tolerant in the pipeline YAML (the primary authoring surface). The flag is off unless the author explicitly turns it on.
- At runtime, when a gate executes:
  - If the gate succeeds, behavior is unchanged.
  - If the gate fails and is NOT fault tolerant, the pipeline aborts as it does today.
  - If the gate fails and IS fault tolerant, the pipeline records the failure, then continues execution along the failed gate's normal outgoing edges.
- The execution log surfaces a tolerated failure distinctly: the user can see that the specific gate failed, why it failed, and that the pipeline chose to continue because the gate was fault tolerant. A tolerated failure is never shown as a successful gate.
- Because a failed gate produces no outputs, downstream gates that reference context values the failed gate would have set will see those values as absent. Default product behavior: the failed fault-tolerant gate contributes no outputs to the execution context, and downstream gates run against whatever context exists. Authors are responsible for designing downstream gates to tolerate the missing values.
- Existing pipelines that do not use the flag behave exactly as before.

## Open Decisions
- Downstream missing-output behavior: the stated default is that a tolerated-failure gate contributes no outputs and downstream gates proceed against the existing context. We should confirm whether any failure marker or status value (e.g. a flag indicating the gate failed) should also be written into the context so later `logic` gates can branch on it, or whether that is intentionally out of scope for this feature.
- Authoring surface beyond YAML: YAML is the confirmed primary surface. Whether the per-gate visual config panel should also expose this flag is undecided and can be scoped separately.
- Naming of the flag as it appears to authors (e.g. `faultTolerant`, `continueOnFailure`) is a wording decision to settle during spec.

## Questions
_None_
