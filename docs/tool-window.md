# Flai tool window

The **Flai** tool window is the place to browse the project's pipelines, fill their inputs and run
them. It is split into the pipeline list on the left and the detail panel — inputs, Run button and
execution log — on the right.

## The pipeline list

Every file in `<project root>/.flai/` whose name ends in `.flai.yaml`, `.flai.yml`, `.flai`, `.yaml`
or `.yml` gets a row. Rows are sorted by file name so the list does not reshuffle when it reloads.

A row that parses shows the pipeline icon, the pipeline `name` from the YAML, and the gate count.

A row that does **not** parse still appears, so a broken file is never silently missing:

- warning icon
- the file name with the pipeline extension stripped (`broken.flai.yaml` → `broken`)
- greyed `invalid YAML` label
- the parser message as the row tooltip

Invalid files appear as error rows on every path — plugin startup, the **Refresh** button and the
automatic reload — because all three go through the same loader.

## Rows with validation problems

A file can parse and still describe a pipeline that cannot run — most often because the visual
editor already wrote a gate to disk before its fields were filled in (an `llm` gate without an
endpoint, a `bash` gate without a command), but also for broken edges, an unknown entry gate or a
cycle. Every rule comes from the core `PipelineValidator`, the same one the runner and the visual
editor use.

Such a row is marked instead of looking healthy:

- warning icon
- the pipeline `name` in bold
- greyed `N issues` (`1 issue` for a single one) in place of the gate count
- a tooltip listing every issue, one per line

Selecting the row shows the normal input form with a warning block under the header: the warning
icon and the full list of messages, one per line.

**Run stays available.** The button is not disabled — starting the run fails with the identical
validator messages in the execution log. This matches the visual editor, which also writes and lets
you run an incomplete pipeline and reports the problems afterwards.

## Automatic refresh

The list follows the files on disk without pressing **Refresh**. It is driven by the IDE's virtual
file system, so it reacts to:

- a pipeline file created, deleted, renamed or moved in `.flai/`
- a pipeline file saved (content changed on disk)
- the `.flai/` directory itself created or deleted

Rules:

- **Saved files only.** Unsaved editor text is ignored — the list updates when the document is
  written, not while you type.
- **Debounced.** Events are collapsed over a 300 ms window, so a burst (for example a Git checkout)
  causes one reload.
- **Silent.** An automatic reload shows no notification and does not save open documents.
- **Non-disruptive.** A reload never cancels a run in progress and the execution log keeps
  streaming. The detail panel may still re-render if the selected file itself changed — including
  switching to the error view when it stopped parsing.

Only pipeline files directly inside `.flai/` matter: a pipeline file elsewhere in the project, a
file in a nested directory under `.flai/`, or a non-pipeline file such as `.flai/notes.txt` does not
trigger a reload.

## Refresh button

**Refresh** in the list header stays as the manual fallback and keeps its previous behaviour: it
saves all open documents, refreshes the virtual file system for `.flai/`, reloads the list, and
shows the "Reloaded N pipelines" notification.

## Selection

After a reload the selection is reconciled against the fresh rows:

1. the row with the same pipeline id, if it is still there;
2. otherwise the row for the same file path;
3. otherwise the selection is cleared and the detail panel returns to its empty state
   ("Select a pipeline to run").

When a row is restored, the detail panel is refreshed with the new instance — so a pipeline that
just became invalid switches to the error view immediately, and one that was fixed switches back to
the input form.

Picking a **different** pipeline in the list clears the previous run: the execution log empties and
the red failure message of the earlier run disappears, so it never shows up under the newly selected
pipeline. A run in progress is never touched — the reconciliation above re-selects the same row and
leaves its log and result alone.

## Detail panel for an unparseable file

Selecting a row that does **not parse** shows a read-only view: warning icon, file name, and the
full parser message in red. There are no input fields and no Run button. Running an unparseable
pipeline is blocked in the service as well, so neither the tool window nor the editor gutter icon
can start one.

A row that parses but has validation problems is different: it keeps the input form and the Run
button, see [Rows with validation problems](#rows-with-validation-problems).

## Related

- [Visual pipeline editor](visual-editor.md)
- [Pipeline YAML spec](pipeline-yaml-spec.md)
