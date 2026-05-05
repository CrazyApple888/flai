# Functional Spec: File Read and Write Gates

## Functional Requirements

- FR-1: The pipeline YAML must support a gate of type `read-file` as a first-class gate type alongside the existing types (`input`, `output`, `llm`, `logic`, `tool`).
- FR-2: A `read-file` gate must require a `path` field specifying the file to read, expressed as a project-root-relative path or an absolute path. No restriction is placed on the scope of the path for reads.
- FR-3: A `read-file` gate must read the text content of the specified file at runtime and store that content in the execution context under a configurable key.
- FR-4: The output key for a `read-file` gate must default to `content` when no `outputKey` field is specified.
- FR-5: A `read-file` gate must accept an optional `outputKey` field that overrides the default context key under which file content is stored.
- FR-6: The `path` field of a `read-file` gate must support `{{variable}}` template syntax, allowing the path to be resolved from a context variable at runtime.
- FR-7: If the file referenced by a `read-file` gate does not exist at runtime, the gate must fail and halt pipeline execution, surfacing an error message that identifies the unresolved file path.
- FR-8: If the file referenced by a `read-file` gate cannot be read due to a permission or I/O error, the gate must fail and halt pipeline execution, surfacing an error message that identifies the path and the cause of failure.
- FR-9: A `read-file` gate must only support text (non-binary) files.
- FR-10: The pipeline YAML must support a gate of type `write-file` as a first-class gate type alongside the existing types.
- FR-11: A `write-file` gate must require a `path` field specifying the destination file, expressed as a project-root-relative path or a path that resolves within the project root subtree.
- FR-12: A `write-file` gate must require a `contentKey` field identifying the execution context variable whose value is written to the file.
- FR-13: A `write-file` gate must write only text content to the destination file. If the execution context variable referenced by `contentKey` holds a non-string value (e.g., a number, boolean, list, or object), the gate must fail and halt pipeline execution with a descriptive error identifying the key and the actual type. Silent coercion or serialization is not permitted.
- FR-14: On a successful write, pipeline execution must continue to the next gate.
- FR-15: If a `write-file` gate fails for any reason (permission denied, path outside project root, `fail-if-exists` mode and file already exists, or other I/O error), the gate must fail and halt pipeline execution, surfacing an error message that identifies the path and the cause of failure.
- FR-16: Both `read-file` and `write-file` gates must be recognized in `.flai.yaml` files that reside in `<project root>/.flai/`.
- FR-17: Both `read-file` and `write-file` gates must support a `label` field for display in the pipeline UI and logs, consistent with other gate types.
- FR-18: A `write-file` gate must accept an optional `mode` field controlling behavior when the destination file already exists. Allowed values: `overwrite`, `append`, `fail-if-exists`. When `mode` is omitted, the gate behaves as if `mode: overwrite` were specified.
- FR-19: When `mode` is `overwrite`, a `write-file` gate must replace the existing file's content entirely with the new content.
- FR-20: When `mode` is `append`, a `write-file` gate must add the new content to the end of the existing file without modifying existing content.
- FR-21: When `mode` is `fail-if-exists` and the destination file already exists, the gate must fail and halt pipeline execution with an error message identifying the path.
- FR-22: If the parent directories of the destination path do not exist at runtime, a `write-file` gate must automatically create the full parent directory tree before writing the file.
- FR-23: A `write-file` gate must reject any resolved `path` that falls outside the project root subtree — including absolute paths that escape the project root and project-root-relative paths that escape via `../` traversal — by failing and halting pipeline execution with an error message identifying the path.
- FR-24: The `path` field of a `write-file` gate must support `{{variable}}` template syntax, allowing the destination path to be resolved from a context variable at runtime, subject to the project-root-scope restriction of FR-23.
- FR-25: On a successful write, a `write-file` gate must store the resolved absolute destination file path in the execution context under the key `writtenPath`, making it available to downstream gates.

## User Stories

**US-1 — Reading a file into a pipeline**
As a pipeline author, I want to add a `read-file` gate that loads a source file's text content into the execution context, so that downstream gates (e.g., an LLM gate) can use the file content without my having to wire up a `tool` gate.

**US-2 — Dynamic file path resolution**
As a pipeline author, I want to specify the `path` of a `read-file` or `write-file` gate using a `{{variable}}` reference, so that the same pipeline definition can operate on different files at runtime based on earlier gate outputs or user input.

**US-3 — Writing pipeline output to a file**
As a pipeline author, I want to add a `write-file` gate that persists a context value (e.g., generated code, a summary, or a review) to a file on disk, so that I can use flai pipelines to produce durable file artifacts.

**US-4 — Readable pipeline YAML**
As a pipeline author, I want `read-file` and `write-file` to be self-explanatory gate types in the YAML, so that the pipeline's intent is clear without requiring me to understand the underlying `tool` gate mechanics for common file operations.

**US-5 — Graceful failure on missing or unreadable file**
As a pipeline author, I want a `read-file` gate to halt the pipeline and report a clear error when the target file is missing or unreadable, so that I can quickly diagnose path or permission problems without inspecting internal logs.

**US-6 — Graceful failure on write error**
As a pipeline author, I want a `write-file` gate to halt the pipeline and report a clear error when it cannot write the destination file (permission failure, path outside project root, mode conflict, etc.), so that I can quickly diagnose path or permission problems.

**US-7 — Controlling write behavior on existing files**
As a pipeline author, I want to control whether a `write-file` gate overwrites, appends to, or refuses to overwrite an existing file, so that I can choose the behavior that matches my pipeline's intent.

**US-8 — Referencing written path in downstream gates**
As a pipeline author, I want to know the exact path where a `write-file` gate wrote its output, so that downstream gates can log, display, or further process the written file.

## Acceptance Criteria

**AC for US-1 / FR-1–FR-5, FR-9, FR-16:**
- Given a `.flai.yaml` with a gate of `type: read-file` and a `path` pointing to an existing text file, when the pipeline runs, then the file's text content is stored in the execution context.
- Given no `outputKey` field is specified, the content is stored under the key `content`.
- Given `outputKey: file_content` is specified, the content is stored under the key `file_content`.
- A `read-file` gate targeting a binary file must fail and halt the pipeline with an error (binary files are a Non-Goal; the gate must not silently produce garbled output).

**AC for US-2 / FR-6, FR-24:**
- Given `path: "{{file_path}}"` on a `read-file` gate and a context variable `file_path` whose value is a valid file path, when the pipeline runs, the gate resolves the template and reads from the resulting path.
- Given `path: "output/{{run_id}}/result.md"` on a `write-file` gate and a context variable `run_id` present in context, when the pipeline runs, the gate resolves the template and writes to the resulting path (subject to project-root scope enforcement).
- Given `path: "{{file_path}}"` and `file_path` is not present in the context, the gate fails and halts the pipeline with an error message indicating the variable is unresolved.
- Given a `{{variable}}` in a `write-file` gate's `path` that resolves to a path outside the project root, the gate fails and halts with an error identifying the path.

**AC for US-3 / FR-10–FR-15, FR-22, FR-25:**
- Given a `.flai.yaml` with a gate of `type: write-file`, a valid in-project `path`, and a `contentKey` referencing a context variable with text content, when the pipeline runs, the text content is written to the file at the specified path.
- Given the parent directories of the destination path do not exist, the gate creates the full directory tree and writes the file successfully.
- After a successful write, execution continues to the next gate (or completes if it is the final gate).
- After a successful write, the key `writtenPath` is present in the execution context and holds the resolved absolute path of the written file.
- Given the context variable referenced by `contentKey` holds a non-string value (number, boolean, list, or object), the gate fails, the pipeline halts, and the error message identifies the key name and the actual type of the value.

**AC for US-4 / FR-17:**
- Given a `label` field on a `read-file` or `write-file` gate, the label text appears in the pipeline UI and execution log for that gate, consistent with how labels appear for other gate types.

**AC for US-5 / FR-7–FR-8:**
- Given the path resolved by a `read-file` gate does not exist on disk, the gate fails, the pipeline halts, and the error message includes the resolved file path.
- Given the file exists but the process lacks read permission, the gate fails, the pipeline halts, and the error message includes the resolved file path and indicates a permission or I/O failure.

**AC for US-6 / FR-15, FR-23:**
- Given a `write-file` gate targets a path where the process lacks write permission, the gate fails, the pipeline halts, and the error message includes the resolved file path and indicates a permission or I/O failure.
- Given a `write-file` gate has a resolved `path` that falls outside the project root subtree (via absolute escape or `../` traversal), the gate fails, the pipeline halts, and the error message identifies the path.

**AC for US-7 / FR-18–FR-21:**
- Given `mode` is omitted and the destination file already exists, the gate overwrites the file.
- Given `mode: overwrite` and the destination file already exists, the gate replaces its content entirely.
- Given `mode: append` and the destination file already exists, the gate adds the new content to the end of the file without modifying existing content.
- Given `mode: append` and the destination file does not exist, the gate creates the file and writes the content (treating it as a new file).
- Given `mode: fail-if-exists` and the destination file already exists, the gate fails, the pipeline halts, and the error message identifies the path.
- Given `mode: fail-if-exists` and the destination file does not exist, the gate creates the file and writes the content successfully.

**AC for US-8 / FR-25:**
- After a `write-file` gate succeeds, the execution context contains `writtenPath` equal to the resolved absolute path of the written file.
- A downstream gate that references `{{writtenPath}}` receives the correct resolved path.

## Edge Cases & Error Scenarios

- **Empty file (read):** A `read-file` gate targeting an existing, zero-byte file succeeds; the context value is an empty string.
- **Empty string content key (write):** If the context variable referenced by `contentKey` in a `write-file` gate is an empty string, the gate writes a zero-byte file (empty content) and succeeds; no error is raised.
- **Missing `contentKey` variable:** If the context variable referenced by `contentKey` does not exist in the execution context at the time the `write-file` gate runs, the gate fails and halts with an error identifying the missing key.
- **Non-string `contentKey` value:** If the execution context variable referenced by `contentKey` holds a non-string value (e.g., a number, boolean, list, or object), the gate must fail and halt pipeline execution with a descriptive error identifying the key and the actual type. Silent coercion or serialization to string is explicitly not permitted.
- **Absolute path on read:** A `read-file` gate must accept an absolute path and treat it as-is without prepending the project root. No scope restriction applies to reads.
- **Absolute path on write escaping project root:** A `write-file` gate must reject an absolute path that falls outside the project root subtree, failing and halting with an error. Absolute paths that resolve inside the project root are permitted.
- **`../` traversal on write:** A project-root-relative `path` containing `../` segments that resolve outside the project root must cause the `write-file` gate to fail with a path-scope error. This restriction does not apply to `read-file`.
- **Template variable resolves to empty string:** If `{{variable}}` in a `path` field resolves to an empty string, the gate fails with an error indicating the resolved path is invalid.
- **`{{variable}}` on write path resolves outside project root:** Treated identically to a static out-of-scope path — gate fails, pipeline halts, error identifies path (FR-23, FR-24).
- **`mode: append` on new file:** The gate creates the file and writes the content; no error is raised.
- **`fail-if-exists` race condition:** When two concurrent pipeline runs both pass the file-existence check before either completes the write, both may succeed and one will overwrite the other. No file locking is required; this is out of scope.
- **Parent directory is a file:** If a segment of the destination `path` already exists as a regular file (not a directory), auto-creation of parent directories will fail; the gate must surface an I/O error identifying the path.
- **Nested template references:** Only single-level `{{variable}}` substitution is required; nested or chained references (e.g., `{{{{var}}}}`) are out of scope.
- **Concurrent pipeline runs writing to same path:** Two concurrent executions writing to the same path may produce interleaved or inconsistent output; no locking is required (out of scope).
- **Large files:** No explicit file size limit is stated in the PM doc; behavior for very large files is treated as an implementation concern.

## Non-Functional Requirements

- **Portability:** Project-root-relative paths must be resolved against the current project's root directory at runtime, ensuring pipelines work correctly when the project is opened on different machines with different absolute base paths.
- **Failure isolation:** A gate failure (read or write error) must not leave the pipeline in a partially committed state that silently continues execution; halting must be immediate and observable in the UI/log.

## Out of Scope

- Binary (non-text) file reading or writing.
- Watching files for changes or triggering pipelines on file system events.
- File rename, delete, copy, or move operations.
- Cloud or remote storage targets (S3, Google Drive, etc.).
- Writing files to paths that resolve outside the project root subtree (restricted for `write-file`; not restricted for `read-file`).
- Coexistence with the `ide.readFile` built-in tool: `read-file` gates and the `ide.readFile` tool gate coexist with no migration path required; deprecating or removing `ide.readFile` is not part of this feature.
- Multi-level or nested `{{variable}}` template resolution in `path` fields.
- Guaranteed safe concurrent writes when multiple pipeline runs target the same file simultaneously (no file locking).
- Explicit file encoding configuration (e.g., specifying UTF-8 vs. UTF-16 as a gate field).
- File size limits or streaming for large files.

## Questions for PM

_None_
