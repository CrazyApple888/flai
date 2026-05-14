# Bash Execution Gate

## Slug
bash-execution-gate

## Problem

Flai pipelines can call registered IDE tools, run LLM steps, branch through logic gates, and produce outputs, but they do not have a first-class gate for executing project-local Bash commands as part of a pipeline. Users who want a pipeline to run tests, invoke build tasks, call project scripts, or transform files through shell tooling must route that work through a generic tool mechanism or leave the workflow partially manual.

This limits automation for common developer workflows where command execution is a central step, especially when LLM-generated or input-provided values need to become command arguments and when stdout, stderr, and exit status need to feed later gates.

## Goals

- Add a first-class Bash execution gate type that can be declared in `.flai.yaml` pipeline files.
- Allow a pipeline author to define the Bash command, working directory, optional environment values, timeout behavior, and output mapping for command results.
- Capture stdout, stderr, exit code, and execution status as structured outputs available to downstream gates.
- Make command failures explicit in pipeline execution events and logs so users can diagnose failed commands from the existing UI.
- Keep execution project-scoped by default, with predictable behavior relative to the IntelliJ project root.
- Document the YAML schema and expected runtime behavior for Bash gates.
- Preserve the existing architecture convention: domain model first, parser support, a dedicated default executor, and service wiring through the current executor list.

## Non-Goals

- Building a full terminal emulator or interactive shell session inside the pipeline UI.
- Supporting long-running daemon management, background processes, or process supervision beyond a single command invocation.
- Adding remote command execution, SSH support, containers, or CI runner integration.
- Replacing the existing `RunCommandTool`; the Bash gate should provide first-class pipeline semantics, while tools remain callable through tool gates.
- Implementing a broad permissions or policy UI in this feature unless the existing platform already provides a clear local mechanism.
- Supporting Windows `cmd.exe` or PowerShell as part of this Bash-specific gate.

## User Stories

- As a pipeline author, I want to add a Bash gate to run `./gradlew test` so that a pipeline can verify code changes before sending results to later gates.
- As a pipeline author, I want to pass values from previous gates into a command template so that generated file paths, symbols, or arguments can be used in shell commands.
- As a pipeline author, I want stdout, stderr, and exit code available in the execution context so that downstream logic or output gates can react to command results.
- As a developer running a pipeline, I want command failures to appear clearly in the execution log so that I can understand what failed without leaving the Flai tool window.
- As a cautious user, I want Bash commands to run from the project root or an explicitly configured project-relative directory so that pipeline behavior is predictable and does not accidentally depend on the IDE process working directory.
- As a documentation reader, I want the YAML spec to show a Bash gate example so that I can author a working pipeline without reading implementation code.

## Acceptance Criteria

- A `.flai.yaml` pipeline may declare a gate with `type: bash`.
- The Bash gate schema supports at least a command string and may support project-relative working directory, environment values, timeout, and output mapping according to the final functional spec.
- The parser recognizes Bash gates and returns a typed domain model without weakening existing gate parsing.
- A dedicated Bash gate executor runs the configured command through Bash, captures stdout, stderr, exit code, and success/failure state, and stores them in the execution context for downstream gates.
- Non-zero exit codes are represented consistently and visibly in execution results; the pipeline behavior on failure is deterministic and documented.
- Cancellation of a running pipeline cancels the active Bash process and rethrows coroutine cancellation rather than swallowing it.
- Execution logs include enough information to identify the gate and diagnose command failure while avoiding unnecessary noise for successful commands.
- Existing gate types and existing pipeline YAML files continue to parse and execute as before.
- Unit tests or focused integration tests cover parsing, successful command execution, non-zero exit behavior, output propagation, timeout or cancellation behavior if implemented, and backward compatibility.
- `docs/pipeline-yaml-spec.md` is updated with the Bash gate schema, outputs, and an example.

## Risks

- Bash execution can run destructive commands, so the feature must avoid surprising behavior and should keep defaults constrained to the current project.
- Shell command templating can introduce quoting and injection risks when values from prior gates are interpolated into commands.
- Long-running commands can block pipeline progress or consume resources if timeout and cancellation behavior are weak.
- Output capture can become noisy or memory-heavy for commands that produce large logs.
- Cross-platform behavior may be confusing because Bash availability differs by operating system and IDE environment.
- Exposing environment variables in logs could accidentally reveal secrets if logging is too verbose.

## Questions

_None_
