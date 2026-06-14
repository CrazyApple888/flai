# Pipeline YAML Specification

Pipelines live in `<project root>/.flai/` and must be named `*.flai.yaml`, `*.flai`, or `*.yaml`.
The gutter run icon appears on `*.flai.yaml` and `*.flai` files.

## Top-level structure

```yaml
id: unique-pipeline-id        # required, used as PipelineId
name: Human Readable Name     # required
description: optional text    # optional
entry: gate-id                # required — ID of the first gate to execute

gates:
  gate-id:
    type: input | output | llm | logic | tool | bash | read-file | write-file
    label: Human label        # optional, defaults to gate-id
    faultTolerant: false      # optional, default false — see "Fault-tolerant gates"
    # ... gate-specific fields

edges:
  - from: gate-id
    fromPort: out             # optional, default "out"
    to: gate-id
    toPort: in                # optional, default "in"
```

## Gate types

### `input`

Entry point. Validates and seeds `ExecutionContext` with initial values.
Missing required fields with no default → pipeline fails immediately.

```yaml
start:
  type: input
  label: User Inputs
  schema:
    - name: query       # context key
      type: STRING      # STRING | NUMBER | BOOLEAN | JSON
      required: true    # optional, default true
      default: null     # optional fallback if value not provided
```

`schema` is optional — if omitted, any inputs passed at run time are accepted as-is.

---

### `output`

Terminal gate. Pipeline stops after this gate executes.
`outputMapping` selects which context variables to surface as final outputs.

```yaml
finish:
  type: output
  label: Result
  outputMapping:
    result: response    # key in output → context variable name
```

If `outputMapping` is empty, no outputs are captured (gate still terminates the pipeline).

---

### `llm`

Calls an LLM endpoint. Renders `promptTemplate` with `{{variable}}` placeholders resolved from `ExecutionContext`.

```yaml
ask-llm:
  type: llm
  label: Ask Claude
  promptTemplate: "Summarize the following:\n\n{{content}}"
  skills:                          # optional list of skill file paths
    - .flai/skills/persona.md
    - .flai/skills/output-format.md
  inputMapping:
    content: raw_text   # template var → context key (maps context into template vars)
  outputMapping:
    response: summary   # LLM output key → context key
  endpoint:
    url: https://api.anthropic.com/v1/messages
    credentialId: anthropic-key   # looked up via IntelliJ PasswordSafe
    model: claude-sonnet-4-6
    params:             # merged into request body (optional)
      max_tokens: 2048
      temperature: 0.7
```

**Template syntax:** `{{varName}}` — replaced by `context.get(varName)`. Unresolved placeholders are left as-is.

**`inputMapping`** is only needed when template variable names differ from context keys.
If omitted, `{{varName}}` resolves directly from the context key `varName`.

**API key resolution** — two options, checked in order:

1. `apiKeyVar` — names a context variable whose value is used as the API key at runtime. Lets a prior `bash` or `read-file` gate supply the key.
2. `credentialId` — key name looked up in IntelliJ `PasswordSafe` under service `"flai/<credentialId>"`.

Exactly one must be present. Both can be present; `apiKeyVar` takes precedence when its context variable is set.

```yaml
# Option A: key from a prior gate (e.g. bash gate that runs `cat ~/.secrets/api-key`)
endpoint:
  url: https://api.anthropic.com/v1/messages
  apiKeyVar: my_api_key   # context variable name
  model: claude-sonnet-4-6

# Option B: key from PasswordSafe (default)
endpoint:
  url: https://api.anthropic.com/v1/messages
  credentialId: anthropic-key
  model: claude-sonnet-4-6
```

**`outputMapping`** default when omitted: `{ "response" → "response" }`.
The raw LLM text is always stored under key `"response"` in the executor output; map it to whatever context key you need.

**Credential storage:** Store API keys via IntelliJ Settings → Passwords. The `credentialId` value is the key name registered under service `"flai/<credentialId>"`.

**Supported API shapes:**
- Anthropic (`content[0].text`)
- OpenAI (`choices[0].message.content`)

**`skills`** (optional) — a list of file paths referencing plain-text skill files. Skills are reusable instruction bundles that are prepended before the gate's `promptTemplate` when the LLM call is made.

- Each path may be absolute or relative. Relative paths are resolved against the **project root** (the parent directory of `.flai/`).
- Skill files are read fresh from disk on each execution (no caching).
- A skill file may contain optional YAML frontmatter (delimited by `---` at the start of the file). Frontmatter is stripped before the skill body is used — it is never sent to the LLM.
- Skills are concatenated in declaration order, each pair separated by a blank line (`\n\n`), followed by the rendered `promptTemplate`. When `promptTemplate` is absent or empty, only skill content is sent.
- Template variable substitution (`{{...}}`) is applied only to `promptTemplate`, never to skill file contents.
- If a skill file does not exist at the resolved path, the gate fails immediately with an error message that names the missing path.
- If a skill file exists but cannot be read (e.g., permission denied or it is a directory), the gate fails with a distinct error identifying the path and the nature of the failure.
- `skills` is accepted only on `llm` gates. Its presence on any other gate type produces a parse-time error.

**Skill file format:** A skill file is a plain text file (conventionally `.md`). The entire file body is treated as opaque instruction text — no structured parsing is performed. Files with any extension are accepted.

**Example skill file** (`.flai/skills/code-review-expert.md`):
```
You are a senior software engineer performing a code review. Focus on correctness, readability, and maintainability. Point out specific line-level issues where possible.
```

**Merged prompt structure** (with two skills and a non-empty `promptTemplate`):
```
<skill 1 body>

<skill 2 body>

<rendered promptTemplate>
```

---

### `logic`

Branches the pipeline. Evaluates `branches` in order; first match wins.
Emits `GateResult.Routed(port)` — the matching edge must use that port as `fromPort`.

```yaml
check-length:
  type: logic
  label: Route by length
  defaultPort: default   # port to use when no branch matches (optional, default "default")
  branches:
    - port: short
      condition:
        type: comparison
        variable: text    # context key
        op: LT
        value: "100"
    - port: long
      condition:
        type: comparison
        variable: text
        op: GTE
        value: "100"
    - port: empty
      condition:
        type: switch
        variable: text
        values: ["", "null"]
    - port: always-path
      condition:
        type: always      # unconditional — use as catch-all branch
```

**Condition types:**

| type | fields | notes |
|------|--------|-------|
| `comparison` | `variable`, `op`, `value` | numeric ops (`GT` `GTE` `LT` `LTE`) parse both sides as `Double`; non-numeric → false |
| `switch` | `variable`, `values` | matches if `context[variable].toString()` is in `values` |
| `always` | — | always matches; use as final catch-all |

**Comparison ops:** `EQ` `NEQ` `GT` `GTE` `LT` `LTE` `CONTAINS` `STARTS_WITH`

**Wiring routed edges:** each branch port needs a corresponding edge:

```yaml
edges:
  - from: check-length
    fromPort: short
    to: short-handler
  - from: check-length
    fromPort: long
    to: long-handler
  - from: check-length
    fromPort: default
    to: fallback
```

---

### `bash`

Runs one non-interactive Bash command from the project root or an explicitly configured project-local working directory.

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
    timedOut: tests_timed_out
```

**Fields:**

| field | required | default | notes |
|-------|----------|---------|-------|
| `command` | yes | — | Bash command string. Supports `{{variable}}` template syntax. |
| `workingDirectory` | no | `.` | Project-root-relative or absolute path inside the project root. Supports templates. |
| `environment` | no | `{}` | String map overlaid onto the inherited process environment. Values support templates. |
| `timeoutSeconds` | no | `120` | Positive integer. Timed-out commands are terminated and fail the gate. |
| `failOnNonZeroExit` | no | `true` | If `false`, non-zero exits produce a successful gate result with `success: false`. |
| `outputMapping` | no | `{}` | Bash output key to context key. Empty mapping stores all raw outputs directly. |
| `label` | no | gate id | Display label in UI and logs. |

**Outputs:**

| output key | type | meaning |
|------------|------|---------|
| `stdout` | string | Captured UTF-8 standard output. |
| `stderr` | string | Captured UTF-8 standard error. |
| `exitCode` | integer | Process exit code. |
| `success` | boolean | `true` only when `exitCode == 0`. |
| `timedOut` | boolean | `false` for completed process results. Timeout failures halt the pipeline. |

**Behavior:**
- The command runs as `/bin/bash -lc <rendered command>`.
- Missing template variables in `command`, `workingDirectory`, or environment values fail the gate before a process starts.
- A rendered blank command or working directory fails the gate.
- Relative working directories resolve against the project root. Absolute paths are accepted only if their canonical path is inside the project root subtree.
- Exit code `0` succeeds unless the process times out or cannot start.
- Non-zero exit fails and halts the pipeline by default. With `failOnNonZeroExit: false`, the gate completes structurally and downstream gates can branch on `exitCode` or `success`.
- Timeout, start failure, invalid working directory, and cancellation stop the active process. Cancellation is rethrown to the pipeline runner.
- Environment values are not logged as a full map to avoid exposing secrets.

`bash` differs from the built-in `ide.runCommand` tool by being a first-class gate with typed YAML fields, Bash-specific timeout and working-directory scoping, and gate-native `outputMapping`.

---

### `read-file`

Reads the text content of a file on disk and stores it in the execution context. Supports project-root-relative and absolute paths. No scope restriction — absolute paths outside the project root are accepted.

```yaml
read-source:
  type: read-file
  label: Read Source File
  path: "{{file_path}}"        # literal or {{variable}} template
  outputKey: file_content      # optional, default "content"
```

**Fields:**

| field | required | default | notes |
|-------|----------|---------|-------|
| `path` | yes | — | Project-root-relative or absolute path to the file. Supports `{{variable}}` template syntax. |
| `outputKey` | no | `content` | Context key under which the file content is stored. |
| `label` | no | gate id | Display label in UI and logs. |

**Behavior:**
- If `path` contains `{{variable}}` references, each variable must be present in the context; missing variables cause an immediate failure.
- If the rendered path is blank, the gate fails.
- Relative paths are resolved against the project root.
- Binary files (NUL byte detected in first 8 KB) cause a failure.
- On success, the file's UTF-8 text content is stored in the context under `outputKey`.

---

### `write-file`

Writes a context variable's string value to a file on disk. The destination path must resolve within the project root subtree.

```yaml
save-result:
  type: write-file
  label: Save Review
  path: "output/{{run_id}}/review.md"   # literal or {{variable}} template
  contentKey: review_text                # context variable to write
  mode: overwrite                        # optional, default "overwrite"
```

**Fields:**

| field | required | default | notes |
|-------|----------|---------|-------|
| `path` | yes | — | Destination path (relative to project root or absolute within it). Supports `{{variable}}` template syntax. |
| `contentKey` | yes | — | Name of the context variable whose value is written. Value must be a `String`. |
| `mode` | no | `overwrite` | Write mode: `overwrite`, `append`, or `fail-if-exists`. |
| `label` | no | gate id | Display label in UI and logs. |

**Behavior:**
- If `path` contains `{{variable}}` references, each variable must be present in the context; missing variables cause an immediate failure.
- If the rendered path is blank, the gate fails.
- The resolved path must fall within the project root subtree; paths that escape via `../` or absolute references outside the root cause a failure.
- If the value stored under `contentKey` is not a `String`, the gate fails with an error identifying the key and actual type.
- Parent directories are created automatically if they do not exist.
- On success, the resolved absolute path of the written file is stored in the context under the key `writtenPath`.

**Write modes:**

| mode | behavior on existing file | behavior on new file |
|------|--------------------------|----------------------|
| `overwrite` (default) | Replaces entire content | Creates file |
| `append` | Adds content to end | Creates file |
| `fail-if-exists` | Fails with error | Creates file |

---

### `tool`

Invokes a registered IDE tool by name. Inputs resolved from context via `inputMapping`; outputs written back via `outputMapping`.

```yaml
read-file-tool:
  type: tool
  label: Read Source File
  tool: ide.readFile            # tool name (must be registered)
  inputMapping:
    path: file_path             # tool input key → context key
  outputMapping:
    content: file_content       # tool output key → context key
```

If `outputMapping` is empty, all tool outputs are written directly to context using their output keys.

**Built-in tools:**

| name | inputs | outputs |
|------|--------|---------|
| `ide.readFile` | `path` (abs or relative to project root) | `content`, `path`, `size` |
| `ide.searchSymbol` | `query`, `scope` (`"project"` or `"all"`) | `symbols` (list), `count` |
| `ide.runCommand` | `command`, `workDir` (optional, default project root) | `output`, `exitCode`, `success` |

---

## Execution model

1. Pipeline starts at `entry` gate.
2. Each gate runs; `ExecutionContext` is mutated by `outputMapping`.
3. Next gate determined by edge with `fromPort = "out"` (default) or the routed port for `logic` gates.
4. `output` gate terminates the pipeline (`GateResult.Success` with no next gate).
5. Any `GateResult.Failure` stops execution immediately and emits `PipelineFailed`, unless the failing gate is `faultTolerant` (see below).

## Fault-tolerant gates

Any gate may set `faultTolerant: true`. The field is optional and defaults to `false`, so existing pipelines are unaffected.

- **Default (`false`)** — a gate failure aborts the run and emits `PipelineFailed` (fail-fast, the behavior above).
- **`true`** — a gate failure does **not** abort the run. The failure is recorded distinctly in the execution log as a *tolerated failure* (never as a success), and execution continues along the gate's normal outgoing edge as if the gate had completed.

Applies to all gate types: `input`, `output`, `llm`, `logic`, `tool`, `bash`, `read-file`, `write-file`.

Behavior of a tolerated failure:

- The failed gate contributes **no outputs** to the `ExecutionContext`. Any context values the gate would have set remain absent; downstream gates run against whatever context already exists. Authors are responsible for designing downstream gates to tolerate missing values.
- Continuation uses the gate's `out` port for every gate type **except `logic`**, which continues along its `defaultPort`. A `logic` gate only fails when no branch matches *and* `defaultPort` is `null`; in that case there is no edge to follow, so the tolerated failure is recorded and the run ends normally. (A `logic` branch evaluating to false is normal success, not a failure, and is unaffected by this flag.)
- If a fault-tolerant gate has no outgoing edge, the failure is recorded and the run ends normally.
- A non-fault-tolerant gate that fails downstream of a tolerated failure still aborts the run (fail-fast still applies per gate).

```yaml
flaky-step:
  type: bash
  label: Optional best-effort step
  command: ./flaky.sh
  faultTolerant: true   # failure here does not abort the run
```

## Full example

```yaml
id: code-review-pipeline
name: Code Review
description: Read a file and ask Claude to review it
entry: inputs

gates:
  inputs:
    type: input
    label: Inputs
    schema:
      - name: file_path
        type: STRING
        required: true

  read:
    type: tool
    label: Read File
    tool: ide.readFile
    inputMapping:
      path: file_path
    outputMapping:
      content: file_content

  review:
    type: llm
    label: Review Code
    promptTemplate: |
      Review this code for issues, style, and improvements:

      ```
      {{file_content}}
      ```
    outputMapping:
      response: review_text
    endpoint:
      url: https://api.anthropic.com/v1/messages
      credentialId: anthropic-key
      model: claude-sonnet-4-6

  result:
    type: output
    label: Review Result
    outputMapping:
      review: review_text

edges:
  - from: inputs
    to: read
  - from: read
    to: review
  - from: review
    to: result
```

## Example: read-file and write-file gates

```yaml
id: file-review-pipeline
name: File Review with Save
description: Read a source file, ask Claude to review it, and save the review
entry: inputs

gates:
  inputs:
    type: input
    label: Inputs
    schema:
      - name: file_path
        type: STRING
        required: true
      - name: run_id
        type: STRING
        required: true

  read-source:
    type: read-file
    label: Read Source File
    path: "{{file_path}}"
    outputKey: file_content

  review:
    type: llm
    label: Review Code
    promptTemplate: |
      Review this code for issues, style, and improvements:

      ```
      {{file_content}}
      ```
    outputMapping:
      response: review_text
    endpoint:
      url: https://api.anthropic.com/v1/messages
      credentialId: anthropic-key
      model: claude-sonnet-4-6

  save-result:
    type: write-file
    label: Save Review
    path: "output/{{run_id}}/review.md"
    contentKey: review_text
    mode: overwrite

  result:
    type: output
    label: Done
    outputMapping:
      savedTo: writtenPath

edges:
  - from: inputs
    to: read-source
  - from: read-source
    to: review
  - from: review
    to: save-result
  - from: save-result
    to: result
```
