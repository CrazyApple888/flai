# Functional Spec: LLM Gate Skills Support

## Functional Requirements

- FR-1: The system must recognize a `skills` key on `llm` gate definitions in `.flai.yaml` pipeline files.
- FR-2: The value of the `skills` key must be a list of file paths (strings). Each path references a skill file on disk.
- FR-3: At runtime, before dispatching an LLM call, the system must read each skill file listed in the gate's `skills` key in order.
- FR-4: The contents of each skill file must be prepended before the gate's `promptTemplate` as framing instructions. The final text delivered to the provider is: skill contents concatenated in declaration order, each pair separated by `\n\n` (two consecutive newlines, producing one blank line between blocks), followed by `\n\n`, followed by the rendered `promptTemplate`. When the gate has no `promptTemplate` or an empty one, the trailing `\n\n` and template are omitted; only skill content is sent. This concatenation is plain text; no role-splitting or provider-specific structuring is implied.
- FR-5: When multiple skills are listed, their content is concatenated in declaration order with a single blank line between each skill and between the last skill and the `promptTemplate`. No conflict resolution is required; each skill is plain text and appends without collision.
- FR-6: Relative skill paths must be resolved against the project root (the parent directory of `.flai/`), consistent with the path resolution convention used by `read-file` and `write-file` gates. Absolute paths must be resolved as-is.
- FR-7: If a referenced skill file is not found at the resolved path at runtime, the gate must fail immediately with an error message that includes the exact path that was not found.
- FR-8: If a referenced skill file exists but cannot be read (e.g., permission denied, I/O error), the gate must fail with a clear error identifying the file path and the nature of the failure, distinct from a not-found error.
- FR-9: An `llm` gate with an absent `skills` key, or with an empty `skills` list, must behave identically to the current behavior (no regression).
- FR-10: The `skills` key must be accepted only on `llm` gate definitions. Its presence on any other gate type (`input`, `output`, `logic`, `tool`) must produce a validation error at parse time.
- FR-11: Skills must work with all supported LLM endpoints (Anthropic and OpenAI). The merge is performed by flai before dispatching; no provider-specific skill handling is required.
- FR-12: Skill files must be read fresh from disk at each gate execution. No caching or pinning of skill file contents across executions is performed in this version.
- FR-13: The same skill file path may appear in multiple gates or multiple pipelines without conflict; each gate reads the file independently.
- FR-14: The `skills` list is scoped per gate. No pipeline-level or project-level inheritance of skills is supported.
- FR-15: A skill file is a plain Markdown file. The entire file body is treated as opaque instruction text; flai performs no structured parsing of the body content. The `.md` extension is conventional but not enforced at parse or runtime — files with any extension are accepted if referenced by path.
- FR-16: A skill file may contain optional YAML frontmatter delimited by `---` markers at the start of the file (standard Markdown/Jekyll convention). If frontmatter is present, flai must strip it before using the file's content; only the body after the closing `---` delimiter is concatenated into the LLM call. Frontmatter is silently ignored and is never sent to the LLM.
- FR-17: If frontmatter delimiters are malformed (e.g., an opening `---` with no closing `---`), the entire file content is treated as body text (no frontmatter stripping attempted). This is not a parse error.
- FR-18: An empty skill file (zero bytes or whitespace only after any frontmatter stripping) contributes empty content to the concatenation and does not cause a runtime error. The blank-line separator is still emitted so that subsequent skills and the `promptTemplate` remain correctly separated.
- FR-19: Skill file contents are passed through as literal text. Template variable substitution (`{{...}}` expressions from the `TemplateRenderer`) is applied only to the gate's `promptTemplate`, never to skill file contents. If a skill file contains `{{...}}` text, that text is forwarded to the LLM unchanged.

## User Stories

**As a pipeline author**, I want to attach one or more skill files to an `llm` gate so that I can augment LLM calls with reusable instruction bundles without duplicating prompt content across pipelines.

**As a pipeline author**, I want to list multiple skill files on a single gate so that I can compose multiple reusable instruction sets (e.g., a persona file and an output-format file) in a single LLM call.

**As a pipeline author**, I want to use relative or absolute file paths to reference skill files so that I can organize skill files flexibly within or outside the project.

**As a pipeline author**, I want a clear, actionable error when a skill file is missing so that I can quickly identify and fix a misconfigured path without inspecting logs extensively.

**As a pipeline author**, I want skills to work regardless of whether my endpoint is Anthropic or OpenAI so that I can use the same skill files across different LLM backends.

## Acceptance Criteria

**FR-1, FR-2 — YAML parsing:**
- A `.flai.yaml` file with a valid `skills` list on an `llm` gate parses without error.
- A `.flai.yaml` file with a `skills` key on a non-`llm` gate is rejected at parse time with a validation error identifying the gate and key.
- A `.flai.yaml` file with `skills: []` (empty list) on an `llm` gate parses without error and executes identically to a gate with no `skills` key.

**FR-3, FR-4, FR-5 — Runtime merge:**
- When a gate has one skill, the skill file body is read, stripped of frontmatter, and prepended before the rendered `promptTemplate` with a single blank line separating them.
- When a gate has two or more skills, they are concatenated in declaration order, each pair separated by `\n\n`, and the rendered `promptTemplate` is appended after `\n\n` following the last skill.
- When the gate's `promptTemplate` is absent or empty and skills are present, the text sent to the provider is the skill content(s) only (no trailing `\n\n`). This is not an error.
- The merge produces a single block of plain text delivered to the provider. No additional role-splitting or system-prompt injection is implied by the merge.
- A gate with no `skills` key produces an LLM call identical to today's behavior.

**FR-6 — Path resolution:**
- Absolute paths are used as-is regardless of project location.
- Relative paths are resolved from the project root (the parent directory of `.flai/`). The resolved absolute path is used for file I/O.
- A relative path `.flai/skills/foo.md` in a project rooted at `/home/user/myproject` resolves to `/home/user/myproject/.flai/skills/foo.md`.

**FR-7 — Missing file error:**
- If a skill file does not exist at the resolved path, the gate fails before sending any LLM request.
- The error message contains the exact resolved path of the missing file.
- The error is surfaced in the pipeline execution log and marks the gate as failed.

**FR-8 — Unreadable file error:**
- If a skill file exists but cannot be read, the gate fails before sending any LLM request.
- The error message identifies the file path and indicates that the failure was an I/O or permission error (not a missing-file error).

**FR-9 — No regression:**
- Existing `llm` gates without a `skills` key continue to execute identically to their current behavior after this change.

**FR-11 — Provider agnosticism:**
- A pipeline using an Anthropic endpoint with skills attached executes successfully.
- A pipeline using an OpenAI-compatible endpoint with skills attached executes successfully.
- No skill-related logic is applied after the request leaves flai (i.e., the merge is complete before HTTP dispatch).

**FR-12 — No caching:**
- If a skill file's contents change between two executions of the same gate, the second execution uses the updated contents.

**FR-13 — Independent reads:**
- Two gates referencing the same skill file path each read the file independently; a failure in one gate does not affect the other.

**FR-15 — File format and extension:**
- A skill file with a `.md` extension is accepted.
- A skill file with a non-`.md` extension (e.g., `.txt`) referenced by path is accepted without error.
- No structured parsing of the skill file body is performed; the raw text is used as-is.

**FR-16 — Frontmatter stripping:**
- A skill file whose first line is `---` followed by YAML content and a closing `---` line sends only the body text after the closing delimiter to the LLM.
- A skill file with no frontmatter sends its entire content to the LLM.
- Frontmatter content (between the delimiters) never appears in the text sent to the provider.

**FR-17 — Malformed frontmatter:**
- A skill file that begins with `---` but has no closing `---` is treated as a plain-body file (no stripping); its full content is used without error.

**FR-18 — Empty skill file:**
- A skill file that is empty or contains only whitespace after frontmatter stripping causes the gate to continue without error; the blank-line separator is still emitted correctly and subsequent skills and the `promptTemplate` render as expected.

**FR-19 — Literal skill content (no template substitution):**
- A skill file containing `{{some_var}}` text delivers that literal string to the LLM unchanged; no substitution is attempted.
- Template variable substitution is applied to the gate's `promptTemplate` after the merge layout is assembled, exactly as it is today, with no change to substitution scope or timing for the `promptTemplate` portion.

## Edge Cases & Error Scenarios

- **Empty `skills` list:** `skills: []` must be treated as equivalent to omitting the key — no error, no behavioral change.
- **Duplicate paths within one gate:** If the same file path appears twice in a single gate's `skills` list, the skill is applied twice in order. No deduplication is performed unless PM specifies otherwise.
- **Skill file is a directory:** If the resolved path points to a directory rather than a file, the gate fails with a clear error (treated as unreadable, not as not-found).
- **Skill file is empty:** An empty skill file (zero bytes or whitespace-only after frontmatter stripping) contributes empty content and does not cause a runtime error. The gate continues; blank-line separators between skills and the `promptTemplate` are still emitted correctly.
- **Very large skill file:** No size limit is defined by this spec; behavior under extreme file sizes is left to the architect's discretion.
- **Path with environment-specific separators:** Paths using OS-native separators must be resolved correctly on the host OS.
- **`skills` key present with a non-list value (e.g., a scalar string):** Must be rejected at parse time with a descriptive validation error.
- **All skills fail to load:** Gate fails before any LLM request is sent; no partial calls are made.
- **First skill loads, second fails:** Gate fails before the LLM request; the first skill's read is discarded.
- **Concurrent pipeline executions referencing the same skill file:** Each execution reads the file independently; no shared mutable state.
- **Skill file with frontmatter only (no body):** A file consisting solely of frontmatter delimiters and YAML content with no text after the closing `---` is treated identically to an empty file — no error, no content contributed.
- **Skill file with unterminated frontmatter:** If the file begins with `---` but never closes with a second `---`, the entire file content is used as body text (no stripping). This is not an error.
- **Non-`.md` extension:** A skill path referencing a file without a `.md` extension is accepted; flai applies no extension validation.
- **Separator when only one skill is present:** With exactly one skill, the final text is `skill-body \n\n promptTemplate`. The blank-line separator between skill and template is still required (no special case for single-skill gates).
- **Definition of "blank line" separator:** A blank line means two consecutive newline characters (`\n\n`) between two content blocks — that is, one empty line visually separates each block. This is a content-level specification; the implementation must produce exactly this character sequence between blocks.
- **Gate with skills but no `promptTemplate` (or empty `promptTemplate`):** If the gate's `promptTemplate` is absent or empty, the final text consists of the skill content(s) only (concatenated in declaration order with `\n\n` between them). The trailing `\n\n` before the template is omitted when the template is absent or empty. This is not an error.
- **Skill file containing `{{...}}` text:** The literal `{{...}}` characters are forwarded to the LLM unchanged. Template substitution is never applied to skill content (see FR-19).

## Non-Functional Requirements

- **Error clarity:** Error messages for missing or unreadable skill files must be actionable without requiring users to inspect stack traces — they must name the exact path.
- **IDE responsiveness:** Skill file reads must not block the IDE UI. The pipeline execution must remain cancellable during skill file loading, consistent with the responsiveness expectation for other gate types.

## Out of Scope

- Skill authoring UI — skills are plain files authored by the user outside of flai.
- Anthropic platform Skills (API-hosted capabilities referenced by ID).
- Skill pinning or versioning — files are always read fresh from disk at runtime.
- Skills on gate types other than `llm` (`input`, `output`, `logic`, `tool`).
- Pipeline-level or project-level skill defaults inherited by all `llm` gates.
- Changing credential or endpoint configuration for gates that do not use skills.
- Skills that influence routing or branching logic.
- A skill registry, marketplace, or discovery mechanism.

## Questions for PM

_None_
