# Repository instructions

## Branches

- Do not prefix branch names with `codex`.
- Follow an existing repository branch pattern. Use `fix/`, `feat/`, or `docs/` when no narrower pattern exists.

## Development workflow

- Follow [Develop and ship a Komelia change](docs/maintainers/development-harness.md).
- Use one isolated worktree for each task. Do not mix unrelated platform work in one worktree or pull request.
- Reproduce a defect and add a regression test before changing production code when the failure can be tested.
- Run the checks required for every changed platform before you report completion.
- Use `.github/pull_request_template.md` for every pull request.

## Release versions

- Use stable Semantic Versioning tags in the form `vMAJOR.MINOR.PATCH`.
- Use a patch release for backward-compatible bug fixes, localization corrections, and release-only maintenance.
- Use a minor release for backward-compatible user-facing features and substantial compatible improvements.
- Use a major release for backward-incompatible changes. While the project remains on `0.x`, use a minor release for a breaking change and mark it as breaking in both languages in the Release notes. Publish `1.0.0` only through an explicit maintainer decision.
- Reset PATCH to `0` for a minor release. Reset MINOR and PATCH to `0` for a major release.
- Do not reuse or modify a published tag. Publish a new patch version for a correction.
- Run `scripts/check-release-version.sh` and `scripts/check-release-notes.sh` before publication.
- Name every GitHub Release `Komelia vMAJOR.MINOR.PATCH`.
- Write Release notes as separate `## 中文` and `## English` sections with `.github/RELEASE_TEMPLATE.md`. Keep translated items in the same order.
- Keep Release notes limited to user-visible changes. Do not add test logs, implementation details, omitted platforms, a full changelog, or repository attribution.
- Keep fixed issues open until the Release and its listed artifacts are published and verified.
