# Architecture: LLM Gate Skills Support

## Overview

This feature adds a `skills` key to the `LlmGate` domain model and threads skill loading through the
`DefaultLlmGateExecutor`. Skills are plain text files read from disk at runtime; their bodies are
concatenated before the rendered `promptTemplate` and the resulting merged string is sent to
`LlmClient.complete` unchanged. No new ports, no new gate type, and no changes to the HTTP client
or the routing layer are required. The touched layers are:

- **domain/model** — `LlmGate` gains a `skills: List<String>` field
- **infrastructure/executor** — a new `SkillLoader` helper class; `DefaultLlmGateExecutor` is updated to call it before dispatching
- **infrastructure/pipeline** — `YamlPipelineParser.parseGate` reads and validates the `skills` key; rejects it on non-llm gates
- **docs** — `docs/pipeline-yaml-spec.md` updated to document the key

---

## Tech Decisions

**Decision:** Introduce a standalone `SkillLoader` class injected into `DefaultLlmGateExecutor`, rather than loading skills inline.  
**Reason:** The loading logic is non-trivial: it must resolve relative vs. absolute paths, distinguish "not found" from "unreadable/directory", and strip YAML frontmatter. Extracting it into a focused class makes all three concerns independently unit-testable. This mirrors how `DefaultReadFileGateExecutor` encapsulates its own I/O rather than pushing it into the executor loop.

**Decision:** `LlmClient` interface is not changed; the prompt passed to `complete(config, prompt)` is the fully merged string.  
**Reason:** FR-11 states the merge is performed by flai before dispatching. Keeping the interface signature as `complete(config: LlmEndpointConfig, prompt: String)` avoids coupling the port to the skills concept and requires no changes to `HttpLlmClient`.

**Decision:** Skill-body template substitution is explicitly suppressed; `TemplateRenderer` is applied to the rendered `promptTemplate` only.  
**Reason:** FR-19 mandates literal passthrough of `{{...}}` expressions inside skill files. Rendering the `promptTemplate` first and then concatenating raw skill bodies ensures no accidental substitution.

**Decision:** Validate the `skills` key per-branch in `parseGate`, not in a separate validation pass.  
**Reason:** Matching the existing style — each `when` branch already handles its own required keys and throws `PipelineLoadException` for unexpected ones. Adding a cross-cutting validator would be disproportionate for a single key constraint.

**Decision:** Path resolution logic (`if (File(path).isAbsolute) File(path) else File(projectRoot, path)`) is replicated from `DefaultReadFileGateExecutor`.  
**Reason:** It is the established convention for project-relative paths and is already tested there. Keeping the exact same idiom avoids introducing an abstraction that is not yet warranted.

**Decision:** "Skill file is a directory" is treated as an "unreadable" error (not "not found").  
**Reason:** `file.exists()` returns `true` for directories. After confirming existence, check `!file.isFile()` to triage: missing → not-found error; exists but not a regular file → unreadable error. This satisfies the spec edge case without special-casing.

---

## Data Model

### Modified: `LlmGate` in `domain/model/Gate.kt`

```kotlin
data class LlmGate(
    override val id: GateId,
    override val label: String,
    val promptTemplate: String,
    val skills: List<String> = emptyList(),          // NEW: ordered list of skill file paths
    val inputMapping: Map<String, String> = emptyMap(),
    val outputMapping: Map<String, String> = mapOf("response" to "response"),
    val endpointConfig: LlmEndpointConfig,
) : Gate()
```

`skills` defaults to an empty list so existing `LlmGate` call sites and tests compile without change (FR-9).

---

## APIs / Interfaces

### New class: `SkillLoader`

Location: `infrastructure/executor/SkillLoader.kt`

```kotlin
class SkillLoader(private val projectRoot: String) {

    /**
     * Reads and returns the body text of every skill path in order.
     * Strips YAML frontmatter from each file before returning its body.
     * Throws [SkillLoadException] on missing or unreadable files.
     */
    suspend fun load(skillPaths: List<String>): List<String>

    companion object {
        /** Strips YAML frontmatter (opening --- ... closing ---) if present. */
        internal fun stripFrontmatter(content: String): String
    }
}

/** Carries the resolved path and a human-readable reason (MISSING or UNREADABLE). */
class SkillLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

`load` is `suspend` and executes file I/O on `Dispatchers.IO`. It catches `CancellationException` before `Exception` per CLAUDE.md convention.

### Updated: `DefaultLlmGateExecutor`

```kotlin
class DefaultLlmGateExecutor(
    private val llmClient: LlmClient,
    private val renderer: TemplateRenderer,
    private val skillLoader: SkillLoader,           // NEW dependency
) : GateExecutor<LlmGate>
```

`execute` signature is unchanged; the merged prompt is assembled internally:

```kotlin
override suspend fun execute(gate: LlmGate, context: ExecutionContext): GateResult {
    return try {
        val skillBodies: List<String> = skillLoader.load(gate.skills)
        val renderedTemplate: String = renderer.render(gate.promptTemplate, context.snapshot())
        val mergedPrompt: String = buildMergedPrompt(skillBodies, renderedTemplate)
        val response = llmClient.complete(gate.endpointConfig, mergedPrompt)
        GateResult.Success(outputs = mapOf("response" to response))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SkillLoadException) {
        GateResult.Failure(e, retryable = false)
    } catch (e: Exception) {
        GateResult.Failure(e, retryable = true)
    }
}
```

Merge rule (pure function, private):

```kotlin
private fun buildMergedPrompt(skillBodies: List<String>, renderedTemplate: String): String {
    return if (skillBodies.isEmpty()) {
        renderedTemplate
    } else {
        val parts = skillBodies + if (renderedTemplate.isNotEmpty()) listOf(renderedTemplate) else emptyList()
        parts.joinToString("\n\n")
    }
}
```

Note: skill bodies are included as-is (no renderer call). The rendered template is appended only when non-empty, which handles the "skills only, no promptTemplate" case (FR-4 edge case).

### Updated: `YamlPipelineParser.parseGate` — `llm` branch

```kotlin
"llm" -> LlmGate(
    id = id,
    label = label,
    promptTemplate = map["promptTemplate"] as? String ?: "",
    skills = parseSkillsList(id, map["skills"]),    // NEW
    inputMapping = parseStringMap(map["inputMapping"]),
    outputMapping = parseStringMap(map["outputMapping"]).ifEmpty { mapOf("response" to "response") },
    endpointConfig = parseEndpointConfig(id, map["endpoint"]),
)
```

`promptTemplate` drops the `throw` — a missing or empty template with skills present is valid (FR-4 edge case in spec). If both are absent the resulting empty string is passed through and `buildMergedPrompt` handles it.

Non-llm gate branches must reject an unexpected `skills` key. Add a guard before the `when` block so it covers every non-`llm` type, including unknown ones:

```kotlin
if (type != "llm" && map.containsKey("skills")) {
    throw PipelineLoadException(
        "Gate '${id.value}' of type '$type' does not support 'skills'"
    )
}
return when (type) {
    // ... existing branches unchanged
}
```

This is placed before the `when` in `parseGate`, so it fires for every known and unknown gate type except `llm`.

New private parser helper:

```kotlin
@Suppress("UNCHECKED_CAST")
private fun parseSkillsList(gateId: GateId, obj: Any?): List<String> {
    if (obj == null) return emptyList()
    val list = obj as? List<*>
        ?: throw PipelineLoadException(
            "Gate '${gateId.value}': 'skills' must be a list of file paths, got ${obj::class.simpleName}"
        )
    return list.mapIndexed { i, item ->
        item as? String
            ?: throw PipelineLoadException(
                "Gate '${gateId.value}': 'skills[$i]' must be a string, got ${item?.let { it::class.simpleName } ?: "null"}"
            )
    }
}
```

### `SkillLoader.stripFrontmatter` specification

Frontmatter is present when the file starts with `---` followed by a newline. The closing delimiter
is a line that is exactly `---` (optionally followed by a newline). If the opening delimiter is
present but no closing delimiter is found, the entire file content is used as body (FR-17).

Implementation using a single Regex:

```kotlin
private val FRONTMATTER_REGEX = Regex(
    """^---\r?\n(.*?\r?\n)?---\r?\n?""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)

internal fun stripFrontmatter(content: String): String {
    val match = FRONTMATTER_REGEX.find(content) ?: return content
    return content.substring(match.range.last + 1)
}
```

---

## File-Level Plan

- `src/main/kotlin/me/drew/flai/domain/model/Gate.kt` — modify: add `skills: List<String> = emptyList()` field to `LlmGate`
- `src/main/kotlin/me/drew/flai/infrastructure/executor/SkillLoader.kt` — create: new class that resolves paths, reads files on `Dispatchers.IO`, strips frontmatter, returns ordered list of body strings; throws `SkillLoadException` for missing or unreadable files
- `src/main/kotlin/me/drew/flai/infrastructure/executor/DefaultLlmGateExecutor.kt` — modify: accept `SkillLoader` as constructor parameter; load skill bodies before rendering the template; assemble merged prompt via `buildMergedPrompt`; catch `SkillLoadException` as non-retryable failure
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParser.kt` — modify: add `parseSkillsList` helper; read `skills` in the `"llm"` branch; make `promptTemplate` optional (default to `""`); add `skills` rejection guard to every non-llm gate branch
- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt` — modify: construct `SkillLoader(projectBasePath)` and pass it to `DefaultLlmGateExecutor`
- `src/test/kotlin/me/drew/flai/infrastructure/executor/SkillLoaderTest.kt` — create: unit tests for `SkillLoader` (see Test Strategy)
- `src/test/kotlin/me/drew/flai/infrastructure/executor/DefaultLlmGateExecutorTest.kt` — create: unit tests for the executor with a fake `LlmClient` and `SkillLoader` (see Test Strategy)
- `src/test/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParserSkillsTest.kt` — create: parser unit tests for the `skills` key (see Test Strategy)
- `docs/pipeline-yaml-spec.md` — modify: document the `skills` key under the `llm` gate section, including path resolution convention, frontmatter handling, and error behavior

---

## Implementation Order

1. **Modify `Gate.kt`** — add `skills` field to `LlmGate` with default `emptyList()`. No other code breaks because it is a default parameter.

2. **Create `SkillLoader.kt`** — implement path resolution, `Dispatchers.IO` reads, directory check, not-found vs unreadable distinction, and `stripFrontmatter`. Write `SkillLoaderTest`.

3. **Modify `DefaultLlmGateExecutor.kt`** — inject `SkillLoader`; implement `buildMergedPrompt`; update `execute` to load skill bodies and merge. Write `DefaultLlmGateExecutorTest`.

4. **Modify `YamlPipelineParser.kt`** — add `parseSkillsList`, wire it into the `llm` branch, make `promptTemplate` optional, add `skills` rejection guards on all other gate branches. Write `YamlPipelineParserSkillsTest`.

5. **Modify `FlaiPipelineUiService.kt`** — construct `SkillLoader(projectBasePath)` and pass to `DefaultLlmGateExecutor`.

6. **Update `docs/pipeline-yaml-spec.md`** — add skills documentation.

---

## Test Strategy

### Unit: `SkillLoaderTest` (`infrastructure/executor/`)

Uses `TemporaryFolder` JUnit rule, identical to `DefaultReadFileGateExecutorTest` pattern.

- `load` returns empty list for empty `skillPaths`
- `load` returns body of a single file (absolute path)
- `load` returns body of a single file (relative path, resolved from projectRoot)
- `load` returns bodies of two files in declaration order
- `load` fails with `SkillLoadException` when file does not exist; message contains resolved path
- `load` fails with `SkillLoadException` when path resolves to a directory; message is distinct from not-found
- `load` fails on second path after first loads successfully (all-or-nothing before return)
- `stripFrontmatter` returns full content when no frontmatter
- `stripFrontmatter` strips frontmatter delimiters and YAML content, returns body only
- `stripFrontmatter` returns full content when opening `---` has no closing `---` (FR-17)
- `stripFrontmatter` returns empty string for frontmatter-only file (body is empty after closing delimiter)
- `stripFrontmatter` handles Windows-style `\r\n` line endings in delimiter lines

### Unit: `DefaultLlmGateExecutorTest` (`infrastructure/executor/`)

Uses a fake `LlmClient` that captures its `prompt` argument and a fake `SkillLoader`.

- No skills: prompt equals rendered template (regression)
- One skill: merged prompt is `skillBody + "\n\n" + renderedTemplate`
- Two skills: merged prompt is `skill1 + "\n\n" + skill2 + "\n\n" + renderedTemplate`
- Skills present, empty `promptTemplate`: merged prompt is `skill1 + "\n\n" + skill2` (no trailing `\n\n`)
- Empty skill body in the middle (skills `["A", "", "B"]` with template `"T"`): asserts the exact merged string `"A\n\n\n\nB\n\nT"` — an empty skill contributes an empty string, producing a doubled blank line at that slot, which is the correct per-spec behavior (separator is still emitted)
- `SkillLoadException` from loader: returns `GateResult.Failure` with `retryable = false`; LLM is never called
- Template substitution is applied to `promptTemplate` but not to skill bodies: a `{{var}}` in a skill body is forwarded verbatim

### Unit: `YamlPipelineParserSkillsTest` (`infrastructure/pipeline/`)

- `llm` gate with valid `skills` list parses; `LlmGate.skills` equals the declared paths in order
- `llm` gate with `skills: []` parses; `LlmGate.skills` is empty list
- `llm` gate with no `skills` key parses; `LlmGate.skills` is empty list
- `llm` gate with `skills` as a scalar string throws `PipelineLoadException`
- `llm` gate with `skills` containing a non-string element throws `PipelineLoadException`
- `input` gate with `skills` key throws `PipelineLoadException` naming the gate and key
- `output`, `logic`, `tool`, `read-file`, `write-file` gates with `skills` key each throw `PipelineLoadException`
- `llm` gate with no `promptTemplate` and a non-empty `skills` list parses without error

All tests are pure JUnit 4 (`runBlocking` for suspend functions), following the existing test-file convention.

---

## Risks

**Risk:** Regex-based frontmatter stripper edge cases.  
**Mitigation:** FR-17 mandates a graceful fallback (return full content if no closing `---`). The regex is covered by dedicated unit tests including malformed cases and both line-ending styles. The regex uses `DOT_MATCHES_ALL` to cross newlines correctly.

**Risk:** Very large skill files blocking or degrading pipeline performance.  
**Mitigation:** All I/O runs on `Dispatchers.IO` and the gate execution is cancellable; the pipeline remains responsive. No size limit is mandated by the spec. If a size cap becomes necessary later it can be added to `SkillLoader` without touching any other layer.

**Risk:** `promptTemplate` becoming optional may silently accept a malformed `llm` gate that previously would have failed at parse time.  
**Mitigation:** The absence of `promptTemplate` alongside an empty `skills` list produces an empty prompt sent to the LLM — this is a degenerate but non-crashing case. A runtime LLM error will surface it to the user. A parse-time warning (not error) could be added later if this proves confusing.

**Risk:** `SkillLoadException` bypasses the existing `retryable = true` path.  
**Mitigation:** Missing/unreadable skill files are configuration errors, not transient I/O failures. Marking them `retryable = false` is intentional and correct. The LLM call path still uses `retryable = true` for network errors.

---

## Questions for Business Analyst

_None_
