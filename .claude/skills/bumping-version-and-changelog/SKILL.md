---
name: bumping-version-and-changelog
description: Use when bumping the flai project version (major/minor/patch), preparing a release, or adding entries to CHANGELOG.md / plugin change notes
---

# Bumping Version and Changelog

## Version

Single source of truth: `gradle.properties` → `version = X.Y.Z`. Edit only there.

- `cli/build.gradle.kts` inherits it via `version = rootProject.version` — never edit.
- `core` declares no version.
- Never hardcode a version in `build.gradle.kts` or `plugin.xml`.

## Changelog

`CHANGELOG.md` at repo root, Keep a Changelog format: `## [X.Y.Z]` sections with `### Added` / `### Changed` / `### Fixed` / `### Dependencies`, newest first, `## [Unreleased]` on top.

Wiring (do not duplicate by hand):
- `org.jetbrains.changelog` Gradle plugin renders the section matching the current version into `patchPluginXml.changeNotes`; falls back to `[Unreleased]`.
- `publishPlugin` depends on `patchChangelog`, which stamps `[Unreleased]` into a release section.

## Bump procedure

1. Bump `version` in `gradle.properties` (minor bump: `X.Y.Z` → `X.(Y+1).0`).
2. Find last release tag: `git tag` — tags are bare `X.Y.Z`, no `v` prefix (`versionPrefix = ""`). If the current `gradle.properties` version has no matching tag, that release is still in progress: add entries to its existing `## [X.Y.Z]` section instead of creating a new one, and only bump the version when actually starting the next release.
3. `git log <lastTag>..HEAD --oneline` → collect changes since release. Include uncommitted working-tree changes if relevant. Subtract items already recorded in existing unreleased sections — no duplicates.
4. Insert `## [X.Y.Z]` section directly under `## [Unreleased]`, only user-facing items: features, fixes, visible behavior, dependency bumps. Skip internal refactors unless they change module layout or workflows.
5. Do not commit unless asked.

## Common Mistakes

- Editing version in `cli/build.gradle.kts` — it is inherited; edit is dead or conflicting.
- Writing change notes into `plugin.xml` — generated at build time from CHANGELOG.md.
- Searching tags with `v` prefix — repo tags have none.
- Deleting the `[Unreleased]` heading — `patchChangelog` and the `changeNotes` fallback need it.
