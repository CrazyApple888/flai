# Dev Report: LLM Gate Skills Support

## Implemented

Added `skills` list support to `llm` gates, allowing users to reference one or more skill files by path in pipeline YAML. Skills are plain text (Markdown) files whose bodies are prepended before the `promptTemplate` before dispatching to the LLM.

**Files created:**
- `src/main/kotlin/me/drew/flai/infrastructure/executor/SkillLoader.kt` — resolves paths (absolute and project-root-relative), reads files on `Dispatchers.IO`, strips YAML frontmatter, distinguishes "not found" vs "unreadable/directory" errors, throws `SkillLoadException`
- `src/test/kotlin/me/drew/flai/infrastructure/executor/SkillLoaderTest.kt` — 12 unit tests covering empty input, absolute/relative paths, multi-file ordering, not-found errors, directory errors, second-path failure, and all frontmatter variants
- `src/test/kotlin/me/drew/flai/infrastructure/executor/DefaultLlmGateExecutorTest.kt` — 7 unit tests covering no-skills regression, one skill, two skills, skills-only (no template), empty skill body in the middle, `SkillLoadException` → non-retryable failure, and literal passthrough of `{{...}}` in skill bodies
- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParserSkillsTest.kt` — 12 unit tests covering valid skills list, empty list, missing key, scalar string (error), non-string element (error), and `skills` key rejection on all non-llm gate types

**Files modified:**
- `src/main/kotlin/me/drew/flai/domain/model/Gate.kt` — added `skills: List<String> = emptyList()` field to `LlmGate`
- `src/main/kotlin/me/drew/flai/infrastructure/executor/DefaultLlmGateExecutor.kt` — injected `SkillLoader`; added `buildMergedPrompt` private function; updated `execute` to load skill bodies, merge, catch `SkillLoadException` as `retryable = false`
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParser.kt` — added `parseSkillsList` helper; wired it into the `llm` branch; made `promptTemplate` optional (defaults to `""`); added `skills` rejection guard before the `when (type)` block for all non-`llm` types
- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt` — constructed `SkillLoader(projectBasePath)` and passed it to `DefaultLlmGateExecutor`
- `docs/pipeline-yaml-spec.md` — documented the `skills` key under the `llm` gate section, including path resolution, frontmatter handling, merge structure, and error behavior

## Deviations

**`SkillLoader` made `open` with `load` as `open fun`:** The architecture doc specifies a "fake `SkillLoader`" in tests but did not address that Kotlin classes and methods are `final` by default. Making the class and method `open` is the minimal change enabling subclassing in tests without introducing an interface that the architecture doc did not prescribe.

**`SimpleTemplateRenderer` used in executor tests instead of a custom lambda:** The architecture specifies a "fake" renderer. `TemplateRenderer` is a plain `interface` (not a `fun interface`), so SAM conversion is not available. Using `SimpleTemplateRenderer` (already established in the codebase for tests) achieves the same behavior with less code.

## Follow-ups

None. No environment variables, migrations, or config changes are required.

## Questions for Architect

_None_
