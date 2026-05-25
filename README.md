# flai

![Build](https://github.com/CrazyApple888/flai/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

> **Agentic pipelines for your IDE.** Wire LLMs, IDE tools, shell commands, and file I/O into reusable YAML workflows — and run them from a gutter icon.

<!-- Plugin description -->
**flai** turns IntelliJ-based IDEs into an agent runtime. Define multi-step LLM pipelines as plain YAML, drop them in `.flai/`, and execute them on any file with a single click. No external orchestrator, no separate UI, no copy-paste between ChatGPT and your editor.

Each pipeline is a graph of **gates** — LLM calls, branching logic, shell commands, file reads/writes, and IDE tools — connected by edges. Outputs of one gate flow into the next via a shared execution context. Credentials live in IntelliJ's `PasswordSafe`. Skills (reusable instruction bundles) are just markdown files on disk.

Built for engineers who want their AI workflows to live in the same repo as the code they operate on — versioned, reviewable, and runnable without leaving the IDE.
<!-- Plugin description end -->

---

## Why flai

- **Pipelines as code.** YAML files in `.flai/`, checked into git. Reviewable in PRs, diffable, branchable.
- **Native IDE integration.** Gutter run icon on `*.flai.yaml` and `*.flai` files, dedicated tool window, live execution log.
- **Bring your own model.** Anthropic and OpenAI response shapes supported out of the box. Endpoint and credentials per-gate.
- **Tools that touch your code.** Built-in `ide.readFile`, `ide.searchSymbol`, `ide.runCommand`, plus first-class `bash`, `read-file`, and `write-file` gates.
- **Skills, not megaprompts.** Compose reusable instruction files (`.flai/skills/*.md`) per LLM gate.
- **Branching logic.** `logic` gates with `comparison` / `switch` / `always` conditions route execution across paths.
- **Secrets stay local.** API keys stored via IntelliJ `PasswordSafe` under `flai/<credentialId>`. Never written to YAML.

## Gate types

| Gate         | Purpose                                                                   |
|--------------|---------------------------------------------------------------------------|
| `input`      | Entry point. Seeds context from typed schema (`STRING`/`NUMBER`/`BOOLEAN`/`JSON`). |
| `llm`        | Calls an LLM endpoint with templated prompt and optional skills.          |
| `logic`      | Branches on context variables (comparison, switch, always).               |
| `tool`       | Invokes a registered IDE tool (file read, symbol search, run command).    |
| `bash`       | Runs a non-interactive shell command with timeout and env overlay.        |
| `read-file`  | Reads a file from disk into context.                                      |
| `write-file` | Writes a context variable to disk (`overwrite` / `append` / `fail-if-exists`). |
| `output`     | Terminal gate. Surfaces selected context values as final results.         |

Full spec: [`docs/pipeline-yaml-spec.md`](docs/pipeline-yaml-spec.md).

## Quick start

1. **Install** flai (see below).
2. Create `.flai/` at your project root.
3. Drop in a pipeline file, e.g. `.flai/code-review.flai.yaml` (or `.flai/code-review.flai`):

   ```yaml
   id: code-review
   name: Code Review
   entry: inputs

   gates:
     inputs:
       type: input
       schema:
         - { name: file_path, type: STRING, required: true }

     read:
       type: read-file
       path: "{{file_path}}"
       outputKey: file_content

     review:
       type: llm
       promptTemplate: |
         Review this code for correctness and style:

         ```
         {{file_content}}
         ```
       endpoint:
         url: https://api.anthropic.com/v1/messages
         credentialId: anthropic-key
         model: claude-sonnet-4-6

     result:
       type: output
       outputMapping: { review: response }

   edges:
     - { from: inputs, to: read }
     - { from: read,   to: review }
     - { from: review, to: result }
   ```

4. **Store your API key.** `Settings → Appearance & Behavior → System Settings → Passwords`, add entry under service name `flai/anthropic-key`.
5. **Run.** Open the YAML file and click the gutter run icon, or use the **Flai Pipelines** tool window on the right.

## Installation

- **From JetBrains Marketplace** (recommended):
  <kbd>Settings/Preferences</kbd> → <kbd>Plugins</kbd> → <kbd>Marketplace</kbd> → search **flai** → <kbd>Install</kbd>

- **From the marketplace site:** [plugins.jetbrains.com/plugin/MARKETPLACE_ID](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) → <kbd>Install to ...</kbd>

- **Manually:** download the [latest release](https://github.com/CrazyApple888/flai/releases/latest) →
  <kbd>Settings/Preferences</kbd> → <kbd>Plugins</kbd> → <kbd>⚙️</kbd> → <kbd>Install plugin from disk…</kbd>

**Compatibility:** IntelliJ IDEA 2025.2+ (and other IntelliJ Platform IDEs on the same build).

## Architecture

Hexagonal — pure-Kotlin domain, IntelliJ-aware infrastructure, Swing UI.

```
domain/         Pipeline, Gate (sealed), ExecutionContext, ports
usecase/        ListPipelines, LoadPipeline, RunPipeline
infrastructure/ executors per gate, HttpLlmClient, YAML parser, IDE tools
ui/             tool window, gutter marker, FlaiPipelineUiService (StateFlows)
```

Adding a new gate type: sealed subclass in `Gate.kt` → `DefaultXxxGateExecutor` → wire in `FlaiPipelineUiService` → parser branch in `YamlPipelineParser.parseGate()`. See [`CLAUDE.md`](CLAUDE.md) for full conventions.

## Development

```bash
./gradlew runIde         # launch sandbox IDE with the plugin
./gradlew test           # run tests
./gradlew buildPlugin    # produce distributable ZIP
./gradlew verifyPlugin   # check IDE compatibility
```

## Roadmap

- [ ] **CLI tool** — run `.flai.yaml` / `.flai` pipelines from the terminal, outside the IDE. Same YAML, same gate types, no IntelliJ required. CI-friendly.
- [ ] **MCP server** — expose flai pipelines as MCP tools so any MCP-compatible host (Claude Desktop, other agents) can invoke them directly.
- [ ] **HTTP gate** — make arbitrary HTTP requests (GET/POST/etc.) from a pipeline; response body and status stored in context.
- [ ] **Vector/RAG gate** — embed text and query a vector store; inject retrieved chunks into context for downstream LLM gates.
- [ ] **Parallel gate** — fan-out execution across multiple branches concurrently; collect results before continuing.

## Documentation

- [Pipeline YAML specification](docs/pipeline-yaml-spec.md) — every gate, every field, every example.