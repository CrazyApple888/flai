# Pipeline YAML Specification

Pipelines live in `<project root>/.flai/` and must be named `*.flai.yaml` (or `*.yaml`).
The gutter run icon appears only on `*.flai.yaml` files.

## Top-level structure

```yaml
id: unique-pipeline-id        # required, used as PipelineId
name: Human Readable Name     # required
description: optional text    # optional
entry: gate-id                # required — ID of the first gate to execute

gates:
  gate-id:
    type: input | output | llm | logic | tool
    label: Human label        # optional, defaults to gate-id
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

**`outputMapping`** default when omitted: `{ "response" → "response" }`.
The raw LLM text is always stored under key `"response"` in the executor output; map it to whatever context key you need.

**Credential storage:** Store API keys via IntelliJ Settings → Passwords. The `credentialId` value is the key name registered under service `"flai/<credentialId>"`.

**Supported API shapes:**
- Anthropic (`content[0].text`)
- OpenAI (`choices[0].message.content`)

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
5. Any `GateResult.Failure` stops execution immediately and emits `PipelineFailed`.

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
