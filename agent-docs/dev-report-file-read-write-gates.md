# Dev Report: File Read and Write Gates

## Implemented

Added `read-file` and `write-file` as first-class gate types to the flai pipeline plugin.

**Files modified:**

- `src/main/kotlin/me/drew/flai/domain/model/Gate.kt` — Added `WriteMode` enum and `ReadFileGate`, `WriteFileGate` sealed subclasses.
- `src/main/kotlin/me/drew/flai/infrastructure/executor/CoroutinePipelineExecutor.kt` — Added `ReadFileGate` and `WriteFileGate` branches to `applyAndTrace`'s `when` expression (both mapped to `emptyMap()` for pass-through output application).
- `src/main/kotlin/me/drew/flai/infrastructure/pipeline/YamlPipelineParser.kt` — Added `"read-file"` and `"write-file"` parse branches in `parseGate()`.
- `src/main/kotlin/me/drew/flai/ui/service/FlaiPipelineUiService.kt` — Registered `DefaultReadFileGateExecutor` and `DefaultWriteFileGateExecutor` in the `pipelineExecutor` list.
- `docs/pipeline-yaml-spec.md` — Added `read-file` and `write-file` gate type sections with field tables, behavior notes, and a combined example pipeline.
- `src/test/kotlin/me/drew/flai/MyPluginTest.kt` — Removed reference to non-existent `MyProjectService` class that was blocking all test compilation (pre-existing breakage from the IntelliJ plugin template).

**Files created:**

- `src/main/kotlin/me/drew/flai/infrastructure/executor/DefaultReadFileGateExecutor.kt` — Full executor: template pre-check, relative/absolute path resolution, NUL-byte binary detection, UTF-8 read via `Dispatchers.IO`.
- `src/main/kotlin/me/drew/flai/infrastructure/executor/DefaultWriteFileGateExecutor.kt` — Full executor: template pre-check, project-root scope enforcement (with symlink resolution), `contentKey` type check, parent-dir auto-creation, `OVERWRITE`/`APPEND`/`FAIL_IF_EXISTS` mode handling via `Dispatchers.IO`.
- `src/test/kotlin/me/drew/flai/infrastructure/executor/DefaultReadFileGateExecutorTest.kt` — 10 pure unit tests (JUnit 4, no IntelliJ platform): all pass.
- `src/test/kotlin/me/drew/flai/infrastructure/executor/DefaultWriteFileGateExecutorTest.kt` — 16 pure unit tests (JUnit 4, no IntelliJ platform): all pass.

**Test results:** 26 tests, 0 failures, 0 errors.

## Deviations

**`MyPluginTest.kt` — removed reference to non-existent `MyProjectService`.** This file is outside the architecture doc's File-Level Plan. It was a pre-existing compile error from the IntelliJ plugin template scaffold (`MyProjectService` was never created). Removing the broken `testProjectService` method was necessary to allow test compilation; the remaining two test methods (`testXMLFile`, `testRename`) are preserved unchanged. The full test suite now passes with `BUILD SUCCESSFUL`.

**Symlink resolution in `DefaultWriteFileGateExecutor` scope check.** The architecture doc specifies `candidatePath = Path.of(resolvedPath).normalize().toAbsolutePath()` (no symlink resolution). On macOS, `java.io.tmpdir` paths (used by JUnit's `TemporaryFolder` rule) are symlinks: `/var/folders/...` → `/private/var/folders/...`. The project root is resolved via `toRealPath()` (as specified), yielding the `/private/...` form. A plain `toAbsolutePath()` on the candidate retains the `/var/...` form, so `startsWith` fails for every temp-dir path. To make the scope check symlink-correct in production and in tests, the executor now additionally calls `toRealPath()` on the candidate when the path (or its parent directory) already exists, falling back to `normalize().toAbsolutePath()` when neither exists. This matches the intent of the architecture (reliable `startsWith` comparison using canonicalized paths) while handling macOS symlinks correctly. This change also closes a potential security gap: a symlink inside the project root pointing outside would pass the spec'd check but fails this one.

## Follow-ups

None.

## Questions for Architect

_None_
