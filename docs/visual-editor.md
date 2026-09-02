# Visual pipeline editor

The **Visual** tab on any pipeline file (`*.flai.yaml`, `*.flai.yml`, `*.flai`, `*.yaml`, `*.yml`) shows the pipeline as a graph: drag gate types from the palette onto the canvas, connect ports, and edit gate fields in the property panel.

## New files

Opening an empty pipeline file seeds the pipeline `id` and `name` from the file name (`my-flow.flai.yaml` → `my-flow`; characters outside `a-z0-9_-` become `-`). The first gate you drop becomes the `entry` gate; deleting the entry gate moves `entry` to the first remaining gate.

## Auto-sync to YAML

Every change on the canvas or in the property panel is written back to the YAML document about 300 ms after the last edit and saved to disk. The **Saved** banner confirms each write. Auto-sync only requires the pipeline to be structurally valid (pipeline `id`, an existing `entry` gate, non-blank unique gate ids); if that fails, the error banner shows what is missing and nothing is written. Auto-sync also refuses to write when the canvas has no gates at all — the error banner asks you to add one.

Auto-sync never overwrites a document that currently has YAML parse errors — fix the YAML in the Text tab first. Before every write, the serialized YAML is parsed back to confirm it round-trips; if that check fails the write is skipped and the error banner explains why. Node positions are stored in a per-project sidecar file outside the YAML.

Auto-sync never opens a confirmation dialog. The first time a sync would rewrite a file that contains comments (`#`) or document markers (`---`), it skips the write instead and the error banner asks you to press **Apply** once. Applying shows the "YAML will be normalized" confirmation described below; accepting it remembers your choice for that file, and auto-sync then continues normally on later changes.

## Apply

**Apply** (or the IDE *Save All* shortcut) runs the full validation — including gate-specific rules such as LLM prompt template, endpoint URL, credential and model — and writes the YAML immediately. Use it to see the complete list of problems before running the pipeline.

The first time Apply would rewrite a file that contains comments (`#`) or document markers (`---`), it asks for confirmation because serialisation normalises the YAML ("Applying will normalize the YAML file..."). Cancelling leaves the file untouched; accepting writes the file and remembers the choice for that file, so neither Apply nor auto-sync asks again.

## Run

**Run** saves the document and executes the pipeline from the file on disk; gate status is shown on the canvas while it runs, and editing is locked until it finishes.
