# Bash Execution Gate Functional Spec

## Slug
bash-execution-gate

## Functional Requirements

- FR-1: The pipeline YAML must support a first-class gate of `type: bash`.
- FR-2: A Bash gate must require a `command` field containing the shell command to execute.
- FR-3: A Bash gate must support the existing `label` field for display in the UI and execution logs, defaulting to the gate id when omitted.
- FR-4: A Bash gate must execute exactly one non-interactive command invocation. Interactive prompts, terminal sessions, background process supervision, and daemon management are out of scope.
- FR-5: The `command` field must support the same `{{variable}}` template syntax used by existing file gates, resolving values from the current `ExecutionContext` immediately before execution.
- FR-6: A Bash gate must support an optional `workingDirectory` field. When omitted, the command runs from the IntelliJ project root.
- FR-7: `workingDirectory` may be project-root-relative or absolute, but its resolved canonical path must be inside the project root subtree.
- FR-8: A Bash gate must support an optional `environment` map whose keys are environment variable names and whose values are strings that may contain `{{variable}}` templates.
- FR-9: Environment variables defined on the gate are added to the inherited process environment for the command invocation. Gate-defined keys override inherited values for that process only.
- FR-10: A Bash gate must support an optional `timeoutSeconds` integer. When omitted, the default timeout is `120` seconds.
- FR-11: `timeoutSeconds` must be greater than zero.
- FR-12: A Bash gate must support an optional `failOnNonZeroExit` boolean. When omitted, it defaults to `true`.
- FR-13: A Bash gate must capture stdout, stderr, exit code, and success state separately.
- FR-14: On successful execution, the gate must expose these raw executor outputs: `stdout`, `stderr`, `exitCode`, `success`, and `timedOut`.
- FR-15: A Bash gate must support `outputMapping` using the same output-key-to-context-key convention as LLM and tool gates. If `outputMapping` is omitted or empty, the raw outputs are stored directly into the execution context.
- FR-16: A command with exit code `0` must be considered successful unless the process times out or cannot be started.
- FR-17: A command with non-zero exit code must fail the gate and halt the pipeline when `failOnNonZeroExit` is `true`.
- FR-18: A command with non-zero exit code must complete the gate successfully when `failOnNonZeroExit` is `false`, with `success: false` and the actual `exitCode` available to downstream gates.
- FR-19: A command that exceeds `timeoutSeconds` must be terminated, fail the gate, and halt the pipeline.
- FR-20: Pipeline cancellation must cancel the active process and rethrow `CancellationException`.
- FR-21: The Bash gate must be implemented as a typed domain gate, not as a wrapper around the generic `tool` gate.
- FR-22: Existing gate types and existing pipeline YAML files must keep parsing and executing as before.
- FR-23: `docs/pipeline-yaml-spec.md` must be updated with the Bash gate schema, output keys, failure behavior, and a working example.

## YAML Behavior

Example:

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

- `type: bash` is the canonical YAML type name.
- Required fields: `command`.
- Optional fields: `label`, `workingDirectory`, `environment`, `timeoutSeconds`, `failOnNonZeroExit`, `outputMapping`.
- `command` must be a YAML string. Blank strings are invalid.
- `workingDirectory` must be a YAML string when present. Blank strings are invalid.
- `environment` must be a YAML map from string keys to string values when present.
- `timeoutSeconds` must be an integer greater than zero when present.
- `failOnNonZeroExit` must be a boolean when present.
- `outputMapping` follows the existing map shape where source keys are executor output names and values are destination context keys.
- Templates in `command`, `workingDirectory`, and `environment` values resolve against the execution context at runtime.
- Missing template variables cause the gate to fail before starting a process.
- Relative `workingDirectory` values resolve against the project root.
- Absolute `workingDirectory` values are accepted only if they resolve inside the project root subtree.

## Execution Behavior

- The executor runs the rendered command through Bash using a non-interactive shell, equivalent to `/bin/bash -lc <rendered command>`.
- The process directory is the resolved `workingDirectory`, or the project root when omitted.
- The process environment starts from the IDE process environment, then applies the rendered gate `environment` values.
- Stdout and stderr are captured as separate UTF-8 text streams.
- Exit code is captured as an integer. If the process cannot be started, no exit code is produced and the gate fails with a start error.
- On exit code `0`, the executor returns `GateResult.Success` with:
  - `stdout`: captured standard output text
  - `stderr`: captured standard error text
  - `exitCode`: `0`
  - `success`: `true`
  - `timedOut`: `false`
- On non-zero exit with `failOnNonZeroExit: false`, the executor returns `GateResult.Success` with the captured streams, actual exit code, `success: false`, and `timedOut: false`.
- On non-zero exit with `failOnNonZeroExit: true`, the executor returns `GateResult.Failure`. The failure message includes the gate label or id and the exit code. Captured stderr should be included in the error message when available, truncated to a concise diagnostic length.
- On timeout, the executor terminates the process, returns `GateResult.Failure`, and reports that the command timed out after the configured number of seconds.
- On coroutine cancellation, the executor terminates the process and rethrows cancellation.
- Successful outputs are applied through `ExecutionContext.applyOutputs()`. This requires the pipeline executor to treat `BashGate` like `LlmGate` and `ToolGate` for output mapping.

## Validation and Errors

- Parse-time validation must reject a Bash gate missing `command`.
- Parse-time validation must reject non-string `command`, non-string `workingDirectory`, non-map `environment`, non-string environment keys or values, non-positive `timeoutSeconds`, and non-boolean `failOnNonZeroExit`.
- Runtime validation must reject rendered blank `command`.
- Runtime validation must reject rendered blank `workingDirectory`.
- Runtime validation must reject any `workingDirectory` that resolves outside the project root, including `../` traversal and absolute paths outside the project.
- Runtime validation must fail when a required template variable in `command`, `workingDirectory`, or `environment` is absent from the context.
- Runtime validation must fail when Bash cannot be started or the working directory does not exist or is not a directory.
- Failure messages must name the gate and the failing field or process condition where practical.
- Failure messages and logs must not print the full rendered environment map, to avoid accidental secret exposure.
- The command string may appear in logs or errors only as a concise identifier for the attempted command; stderr/stdout remain captured outputs and should not be dumped wholesale on successful execution.
- Cancellation must be handled before generic exceptions and must not be swallowed.

## UI Behavior

- The existing pipeline list, detail view, and execution log must display Bash gates consistently with other typed gates.
- Gate start and completion events should use the gate label when provided.
- Successful Bash gates should appear as normal completed gates in the execution log, with duration captured by the existing tracing mechanism.
- Failed Bash gates should appear as failed gates with a concise error message that includes timeout, start failure, or non-zero exit code as applicable.
- The UI does not need an embedded terminal, streaming output pane, command prompt, confirmation dialog, or permission-management screen for this feature.
- Downstream output gates may surface mapped Bash outputs in the same way they surface outputs from LLM and tool gates.

## Acceptance Tests

- AT-1: Given a pipeline with `type: bash` and `command: "printf hello"`, when parsed, the parser returns a typed `BashGate` with default `workingDirectory`, `timeoutSeconds`, `failOnNonZeroExit`, and empty `outputMapping`.
- AT-2: Given a Bash gate without `command`, parsing fails with a `PipelineLoadException` naming the gate and missing field.
- AT-3: Given `command: "printf '{{name}}'"` and context `name=world`, execution stores `stdout=world`, `stderr=""`, `exitCode=0`, `success=true`, and `timedOut=false`.
- AT-4: Given `outputMapping: { stdout: command_output }`, execution stores stdout under `command_output` and does not require downstream gates to read `stdout`.
- AT-5: Given `workingDirectory: "subdir"` inside the project root, execution runs from that directory.
- AT-6: Given `workingDirectory: "../outside"` or an absolute path outside the project root, execution fails before starting the process.
- AT-7: Given an `environment` value containing a template, execution passes the rendered variable to the process and captures output proving the value was available.
- AT-8: Given a command that writes to stderr and exits `0`, execution succeeds and captures stderr separately from stdout.
- AT-9: Given a command that exits `2` and `failOnNonZeroExit` is omitted, the gate fails and the pipeline halts.
- AT-10: Given a command that exits `2` and `failOnNonZeroExit: false`, the gate succeeds structurally, stores `exitCode=2` and `success=false`, and downstream logic can route on the mapped exit code.
- AT-11: Given a command that exceeds `timeoutSeconds`, the process is terminated and the gate fails with a timeout message.
- AT-12: Given pipeline cancellation while a Bash command is running, the process is terminated and cancellation is rethrown.
- AT-13: Existing YAML files containing `input`, `output`, `llm`, `logic`, `tool`, `read-file`, and `write-file` gates continue to parse.
- AT-14: `docs/pipeline-yaml-spec.md` contains a Bash gate section documenting fields, defaults, outputs, failure behavior, and an example.

## Questions for PM

_None_
