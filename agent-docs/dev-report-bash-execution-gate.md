# Bash Execution Gate Dev Report

## Slug
bash-execution-gate

## Implemented

- Added `BashGate` to the domain gate model with command, working directory, environment, timeout, non-zero-exit policy, and output mapping fields.
- Added YAML parser and serializer support for `type: bash`, including strict Bash field validation and default-field omission on serialization.
- Added `DefaultBashGateExecutor` using `/bin/bash -lc`, template preflight checks, project-root working-directory containment, inherited environment overlays, concurrent stdout/stderr capture, timeout handling, non-zero-exit policy, and cancellation cleanup.
- Wired Bash output mapping into `CoroutinePipelineExecutor` and registered `DefaultBashGateExecutor` in `FlaiPipelineUiService`.
- Added visual editor support: palette entry, default node creation, property editing, label/id rebuild handling, validator checks, theme color, and icon fallback.
- Updated `docs/pipeline-yaml-spec.md` with Bash schema, defaults, outputs, failure behavior, scoping, timeout behavior, and example YAML.

## Tests

- Added focused executor tests for stdout/stderr capture, templating, missing template variables, environment templates, working directory behavior, outside-project rejection, non-zero-exit behavior, timeout, and cancellation.
- Added pipeline integration tests for Bash output mapping into a final output gate and non-zero failure halting downstream execution.
- Extended parser/serializer tests for minimal Bash parsing, invalid Bash field validation, optional-field round-trip, default omission, and all-gate round-trip coverage.
- Updated visual tests for palette filtering, ports, validation, and theme coverage.
- Verified with:
  - `./gradlew test`
  - `./gradlew build`

## Follow-ups

- Consider adding a dedicated terminal-style icon instead of reusing `FlaiIcons.GATE_TOOL`.
- Consider a future confirmation/policy layer for command execution if the product scope expands beyond the current spec.

## Questions for Architect

_None_
