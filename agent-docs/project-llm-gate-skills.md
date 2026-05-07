# Feature: LLM Gate Skills Support

## Slug
llm-gate-skills

## Summary
Allow users to attach "skills" to an `llm` gate so the LLM call is augmented with reusable, pre-defined instruction/prompt bundles. A skill is a user-authored file on disk (not an Anthropic platform feature). The user references a skill by its file path directly in the pipeline YAML, and flai merges the skill's contents into the LLM call at runtime. This makes it possible to compose richer, modular LLM behavior — for example, applying a shared code-review instruction bundle across many pipelines — without duplicating prompt content. Skills are endpoint-agnostic: they work with any supported provider (Anthropic or OpenAI) because flai controls the merge, not the API.

## Goals
- Let users reference one or more skill files by path in an `llm` gate's YAML to augment the LLM call with reusable instructions
- Keep skills declarative and YAML-authored, consistent with how other gate options (`inputMapping`, `outputMapping`, `params`) are expressed today
- Use an open, portable file format for skill files so they are not proprietary to flai
- Allow the same skill file to be reused across multiple gates or pipelines without repetition
- Surface a clear, actionable error when a referenced skill file is not found at runtime

## Non-Goals
- Creating a skill authoring UI — skills are authored as plain files and referenced by path in the pipeline YAML
- Anthropic platform Skills (API-hosted capabilities referenced by ID) — this feature is exclusively about flai-native file-based skills
- Modifying existing gate types other than `llm`
- Changing how credentials or endpoint configuration work for gates that do not use skills
- Skills that alter routing or branching logic (those belong to `logic` gates)

## User-Facing Behavior

**Skill file format**

A skill file is a plain Markdown (`.md`) file. Its entire body is the instruction text — a persona, behavioral constraint, output format requirement, or any reusable LLM guidance. No required schema or top-level keys. Optional YAML frontmatter (e.g., `name`, `description`) is permitted for human documentation purposes but is ignored by flai at runtime.

Example skill file (`.flai/skills/code-review-expert.md`):
```markdown
You are a senior software engineer performing a code review. Focus on correctness, readability, and maintainability. Point out specific line-level issues where possible. Do not suggest rewrites of code that is not shown.
```

**Referencing skills in a gate**

1. The user creates one or more skill files anywhere on disk. The conventional location is `.flai/skills/` inside the project, but this is not enforced.
2. The user adds a `skills` key to an `llm` gate in their `.flai.yaml`. The value is a list of file paths (relative or absolute).
3. Relative paths are resolved against the **project root** (the parent directory of `.flai/`), consistent with how `read-file` and `write-file` gates resolve paths.

Example YAML:
```yaml
ask-with-skills:
  type: llm
  label: Ask Claude with Skills
  promptTemplate: "Review this code:\n\n{{file_content}}"
  skills:
    - .flai/skills/code-review-expert.md
    - .flai/skills/strict-output-format.md
  endpoint:
    url: https://api.anthropic.com/v1/messages
    credentialId: anthropic-key
    model: claude-sonnet-4-6
```

**Runtime behavior**

4. At runtime, flai reads each skill file in the order listed. Skill content is sent to the LLM as instructions that frame the prompt — separate from, and prepended before, the gate's `promptTemplate` content. Skills are concatenated in declaration order, separated by a blank line. The gate's `promptTemplate` follows after all skill content.
5. When multiple skills are listed, their content is always concatenated in the order declared. There are no conflicting fields to resolve — each skill file is plain text, so multiple skills always append to each other without collision.
6. If a referenced skill file is not found at the given path, the gate fails immediately with a clear error identifying the missing path. No skills are sent and the LLM is not called.
7. Skills work with any supported endpoint (Anthropic or OpenAI) — flai manages how skill content is delivered to the provider.
8. Skills configuration is per-gate; no pipeline-level or project-level default skill inheritance is implied.

## Open Decisions

- **Skill versioning:** Skills are always resolved from the file on disk at runtime (no pinning). Whether to support a future mechanism to pin a skill to a specific file revision is not yet decided.
- **Pipeline-level defaults:** The current design is per-gate only. Whether to support a future pipeline-level `skills` list that is inherited by all `llm` gates in that pipeline is not yet decided.

## Questions
_None_
