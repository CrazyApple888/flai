# Visual pipeline editor

The **Visual** tab on any pipeline file (`*.flai.yaml`, `*.flai.yml`, `*.flai`, `*.yaml`, `*.yml`) shows the pipeline as a graph: drag gate types from the palette onto the canvas, connect ports, and edit gate fields in the property panel.

## New files

Opening an empty pipeline file seeds the pipeline `id` and `name` from the file name (`my-flow.flai.yaml` → `my-flow`; characters outside `a-z0-9_-` become `-`). The first gate you drop becomes the `entry` gate; deleting the entry gate moves `entry` to the first remaining gate. A dropped gate gets the id `<gate type>_<epoch milliseconds>` (for example `llm_1731582394512`); rename it in the property panel.

## Auto-sync to YAML

Every change on the canvas or in the property panel is written back to the YAML document about 300 ms after the last edit and saved to disk. The **Saved** banner confirms each write. Auto-sync only requires the pipeline to be structurally valid (pipeline `id`, an existing `entry` gate, non-blank unique gate ids); if that fails, the error banner shows what is missing and nothing is written. Auto-sync also refuses to write when the canvas has no gates at all — the error banner asks you to add one.

Auto-sync never overwrites a document that currently has YAML parse errors — fix the YAML in the Text tab first. Before every write, the serialized YAML is parsed back to confirm it round-trips; if that check fails the write is skipped and the error banner explains why. Node positions are stored in a per-project sidecar file outside the YAML.

Auto-sync never opens a confirmation dialog. When the file's current formatting differs from what the editor would write — YAML comments, custom key order, extra blank lines and the like — the first sync skips the write and the error banner asks you to press **Apply** once. Applying shows the "YAML will be normalized" confirmation described below; accepting it remembers your choice for that file, and auto-sync then continues normally on later changes. A `#` inside a prompt or command does not trigger this on its own; only a real formatting difference does.

Gate field rules — LLM prompt template, endpoint URL, credential and model, tool name, bash command, file paths — do **not** block auto-sync. A gate you have just dropped is written to the YAML file straight away with its fields still empty, so nothing is lost while you configure it. Those rules are reported by **Apply** and **Run**, and they block execution only.

## Apply

**Apply** (or the IDE *Save All* shortcut) writes the YAML immediately and then runs the full validation — the core rules (port names, one outgoing edge per port, no cycles) plus the gate-specific rules. Any problems are listed in a "Validation Errors" dialog *after* the file has been written; the file is on disk either way. Use it to see the complete list of problems before running the pipeline. Only an unparsable document or a structural problem (missing pipeline `id`, missing `entry` gate, blank or duplicate gate ids) stops the write.

The first time Apply would change the file's formatting (comments, custom layout), it asks for confirmation because serialisation normalises the YAML ("Applying will normalize the YAML file..."). Cancelling leaves the file untouched; accepting writes the file and remembers the choice for that file, so neither Apply nor auto-sync asks again.

Changing a tool checkbox in the property panel is an ordinary edit: it is picked up by auto-sync and never opens a dialog.

## Run

**Run** first flushes any pending visual edits to the document (the same checks as auto-sync apply) and saves it. If the sync is blocked — parse errors, missing gates, structural problems or a pending normalization confirmation — the error banner shows why and the pipeline is not started. Otherwise the file is written, and the full validation runs: unfinished gates are listed in the same "Validation Errors" dialog Apply shows. The run is started regardless, so the failure is also recorded in the execution log. Gate status is shown on the canvas while it runs, and editing is locked until it finishes; running a different pipeline does not lock this editor.
