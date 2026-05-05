# Feature: File Read and Write Gates

## Slug
file-read-write-gates

## Summary
Add two new first-class gate types — `read-file` and `write-file` — to the flai pipeline YAML. A `read-file` gate reads the content of a file on disk and injects it into the execution context, making file content available to downstream gates (e.g., an LLM gate) without requiring the user to wire up a generic `tool` gate. A `write-file` gate takes a value from the execution context and writes it to a file on disk, enabling pipelines to persist outputs such as generated code, summaries, or reports. Together, these gates make file I/O a natural, readable part of pipeline composition.

## Goals
- Give users a concise, self-explanatory gate type for reading file content into a pipeline
- Give users a concise, self-explanatory gate type for writing pipeline output to a file
- Make pipeline YAML more readable by replacing verbose `tool`-gate boilerplate for common file operations
- Support project-root-relative paths by default so pipelines are portable across machines

## Non-Goals
- Binary/non-text file reading or writing
- Replacing or removing the existing `ide.readFile` built-in tool — it coexists with `read-file` gates; no migration path is required
- Watching files for changes or triggering pipelines on file system events
- File rename, delete, copy, or move operations
- Cloud/remote storage targets (S3, GDrive, etc.)
- Writing files outside the project root subtree (absolute paths that escape the project root are not supported for `write-file`)

## User-Facing Behavior

### `read-file` gate

1. The user adds a gate of type `read-file` to their `.flai.yaml` pipeline.
2. The gate requires a `path` field — a project-root-relative or absolute path to the file to read.
3. The `path` value may be a literal string or a `{{variable}}` template reference resolved from the execution context at runtime, enabling dynamic paths.
4. At runtime, the gate reads the file and writes its text content into the execution context under a configurable key (default key: `content`).
5. If the file does not exist or cannot be read, the gate fails and halts the pipeline with an error message identifying the path.

Example YAML:

```yaml
read-source:
  type: read-file
  label: Read Source File
  path: "{{file_path}}"
  outputKey: file_content   # optional, default "content"
```

### `write-file` gate

1. The user adds a gate of type `write-file` to their `.flai.yaml` pipeline.
2. The gate requires a `path` field and a `contentKey` field identifying which context variable holds the text to write. The value stored under `contentKey` must be a string; if the value is any other type (number, list, object, etc.) the gate fails with a descriptive error rather than silently coercing or serializing the value. This keeps pipeline behavior explicit and prevents unexpected output formats.
3. The `path` value may be a literal string or a `{{variable}}` template reference resolved from the execution context at runtime, enabling dynamic paths. The resolved path must fall within the project root subtree; paths that resolve outside it (via absolute references or `../` traversal) cause the gate to fail with an error.
4. The gate accepts an optional `mode` field controlling behavior when the target file already exists. Allowed values: `overwrite` (default), `append`, `fail-if-exists`. If `mode` is omitted, the file is overwritten.
5. If the target file's parent directory does not exist, the gate automatically creates the full parent directory path before writing.
6. On success, the gate writes the resolved file path into the execution context under the key `writtenPath`, allowing downstream gates to reference or log the output location.
7. If the write fails for any reason (permission denied, path resolves outside project root, `fail-if-exists` and file already exists, `contentKey` value is not a string, etc.), the gate fails and halts the pipeline with an error message identifying the cause.

Example YAML:

```yaml
save-result:
  type: write-file
  label: Save Review
  path: "output/{{run_id}}/review.md"
  contentKey: review_text   # context variable to write
  mode: overwrite            # optional, default "overwrite"
```

## Open Decisions
_None_

## Questions
_None_
