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

### `tool`

Invokes a registered IDE tool by name. Inputs resolved from context via `inputMapping`; outputs written back via `outputMapping`.

```yaml
read-file:
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
