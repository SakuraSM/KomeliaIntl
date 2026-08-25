# Task routes

Choose the narrowest route that covers the request. If a task crosses rows, combine their boundaries and checks.

## Shared UI, navigation, and accessibility

- Use when: changing screens, adaptive layouts, tabs, overlays, focus, motion, strings, or shared reader controls.
- Read first: [quality rules](quality-rules.md), `komelia-ui/build.gradle.kts`, and the relevant `komelia-ui/src/commonMain` screen/state files.
- Focus: shared tokens and state ownership first; platform source sets only for platform behavior.
- Validate: `:komelia-ui:allTests`, Android/Desktop/Wasm builds, UI matrix, Back/Esc, locale, theme, and reduced motion as affected.
- Output: regression test plus redacted visual evidence for rendering changes.

## Network, authentication, and remote/LAN switching

- Use when: changing server addresses, probes, session state, connectivity reactions, or login flows.
- Read first: [network flow](../project-knowledge/flows.md), domain repositories, app platform connectivity implementations, and persisted settings.
- Focus: deterministic address selection, credential boundaries, cancellation, and recovery.
- Validate: unit tests for selection/state order, login against an authorized server, connectivity change on Android, and one non-Android target.
- Output: no credentials, addresses, or response bodies in tests, logs, screenshots, or pull requests.

## Offline downloads, cache, and logs

- Use when: changing download lifecycle, local browsing, deletion, offline filters, cache records, or error logs.
- Read first: `komelia-domain/offline`, database modules, platform storage implementations, and [offline quality rules](quality-rules.md).
- Focus: restart safety, partial files, stale rows, format support, cancellation, delete semantics, and useful redacted errors.
- Validate: repository tests and real add/read/remove/restart/error scenarios on each affected native platform.
- Output: explicit cache compatibility and cleanup behavior.

## Database and persistent settings

- Use when: changing schema, migrations, serialized values, theme/language settings, or cached state.
- Read first: database shared/sqlite/wasm modules and every consumer of the changed value.
- Focus: forward migration, previous data, rollback expectations, default values, and platform parity.
- Validate: migration from the previous schema, repository tests, UI tests, and consuming targets.
- Output: migration and compatibility note; never silently rewrite a persisted enum value.

## Image, PDF, and EPUB readers

- Use when: changing reader navigation, chrome, gestures, progress, rendering, or publication styles.
- Read first: reader UI/state in `komelia-ui`, WebView infrastructure, both EPUB packages, and [reader rules](quality-rules.md).
- Focus: gesture arbitration, Back stack, progress stability, safe areas, publication style isolation, and retries.
- Validate: relevant common tests, package checks/builds, `buildEpubReaders`, consuming apps, and real content types.
- Output: exact event sequence and content type used for verification.

## Native dependencies and packaging

- Use when: changing CMake, JNI, image decoders, WebView, ONNX Runtime, desktop installers, or Android packages.
- Read first: `cmake/`, relevant infrastructure module, root aliases, and Release workflow.
- Focus: architecture, host/target distinction, generated resources, licenses, submodules, and package identity.
- Validate: native dependency build, consuming application build, then install or launch the package.
- Output: target architecture, host, artifact name, and runtime result.

## Localization

- Use when: changing user-facing copy, locale selection, Compose Resources, EPUB controls, metadata labels, or Release messages.
- Read first: both language resource sets, [Simplified Chinese guide](../i18n/CONTRIBUTING_zh-CN.md), and [glossary](../i18n/glossary_zh-CN.md).
- Focus: semantic parity rather than word-for-word translation, overflow, plural/count behavior, and locale application timing.
- Validate: resource compilation, UI tests where logic changes, and English/Simplified Chinese runtime checks.
- Output: both languages in one change unless a translation-only issue explicitly scopes otherwise.

## Upstream synchronization

- Use when: merging or rebasing from `Snd-R/Komelia`, adapting module structure, or resolving fork/upstream divergence.
- Read first: current remotes, divergence, dirty worktrees, submodules, fork-specific features, and [development map](../project-knowledge/dev-map.md).
- Focus: safety checkpoint, isolated simulation, module-by-module conflict resolution, persisted compatibility, localization, network switching, offline support, and package metadata.
- Validate: full affected tests and Android/Desktop/Wasm builds; compare fork features after integration.
- Output: upstream commit, preserved fork capabilities, conflict decisions, and unresolved dependency/environment blocks.

## Issue fix, pull request, and Release

- Use when: processing GitHub issues or publishing a version.
- Read first: [templates](templates.md), [versioning](../maintainers/versioning.md), and [Release procedure](../maintainers/release-and-issue-replies.md).
- Focus: reporter language, regression evidence, correct SemVer level, concise bilingual notes, and asset verification.
- Validate: release policy scripts, package builds in scope, uploaded asset names, and installation/launch.
- Output: keep the issue open until the published Release and relevant assets are verified; then reply and close.
