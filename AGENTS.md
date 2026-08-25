# Komelia repository guide

This file is the entry map. The versioned engineering contract lives under [`docs/harness/`](docs/harness/README.md).

## Project facts

- Purpose: cross-platform Komga client with Android, desktop, Wasm, offline reading, EPUB readers, and Komf browser extensions.
- Primary languages: Kotlin, TypeScript/JavaScript, C/C++, SQL, and shell.
- Base branch: `main`.
- Main map: [`docs/project-knowledge/dev-map.md`](docs/project-knowledge/dev-map.md).

## Toolchain

- Use the Gradle wrapper; do not substitute a system Gradle.
- Release CI runs JDK 21 while Kotlin/JVM outputs target JVM 17.
- `.node-version` declares Node 20; Release CI currently uses Node 24. Treat both as supported declarations until the version split is resolved.
- Clone and update submodules recursively before native builds.

## Common commands

- Harness check: `node scripts/check-harness.mjs`
- Shared UI tests: `./gradlew :komelia-ui:allTests`
- Android debug: `./gradlew androidDebug`
- Desktop JAR: `./gradlew desktopJar`
- Wasm app: `./gradlew komfWebUI`
- EPUB readers: `./gradlew buildEpubReaders`
- Release policy tests: `scripts/test-release-policy.sh`

A listed command is not evidence that it ran. Report executed, failed, blocked, and skipped checks separately.

## Read first

- Change protocol: [`docs/harness/change-protocol.md`](docs/harness/change-protocol.md)
- Test selection: [`docs/harness/testing-guide.md`](docs/harness/testing-guide.md)
- Task routes: [`docs/harness/task-routes.md`](docs/harness/task-routes.md)
- Quality rules: [`docs/harness/quality-rules.md`](docs/harness/quality-rules.md)
- Delivery templates: [`docs/harness/templates.md`](docs/harness/templates.md)
- Module boundaries: [`docs/project-knowledge/contexts.md`](docs/project-knowledge/contexts.md)
- Stable flows and risks: [`docs/project-knowledge/flows.md`](docs/project-knowledge/flows.md)

## Hard boundaries

- Do not prefix branches with `codex`; follow repository patterns such as `fix/`, `feat/`, and `docs/`.
- Use one isolated worktree per task and preserve unrelated or uncommitted user changes.
- Do not edit paths or revisions declared in `.gitmodules` unless dependency work is explicitly in scope.
- Do not commit credentials, signing material, private server data, local SDK paths, private filenames, or unredacted screenshots.
- Preserve Komga contracts, persisted values, database compatibility, Android identity/signing expectations, reader progress, and Release policy unless the task explicitly changes them.
- Keep English and Simplified Chinese user-facing resources synchronized.
- Do not publish, merge, close issues, or alter Releases unless the request explicitly authorizes that external action.

## Completion contract

- Reproduce before fixing when practical; add a regression test for testable defects.
- Keep changes inside the impact map and separate unrelated concerns.
- Run focused checks while editing and the required platform matrix before delivery.
- Record exact evidence and blockers; never report an unrun scenario as passed.
- Review the complete diff and confirm only task-owned files changed.
- Update the project map when entry points, responsibilities, contracts, or test strategy change.
- For Releases, follow [`docs/maintainers/versioning.md`](docs/maintainers/versioning.md) and keep fixed issues open until the published assets are verified.
