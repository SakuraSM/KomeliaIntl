# Komelia engineering harness

This directory is the canonical engineering contract for turning a request into an evidence-backed Komelia change. It is a Standard-tier harness because the repository has multiple applications, platforms, native dependencies, publication paths, and persistent-data boundaries.

## Start here

| Need | Read |
|---|---|
| Understand the repository | [Development map](../project-knowledge/dev-map.md) and [bounded contexts](../project-knowledge/contexts.md) |
| Plan or implement a change | [Change protocol](change-protocol.md) |
| Select checks | [Testing guide](testing-guide.md) |
| Deliver an Android test APK for user validation | [Android device validation](android-device-validation.md) |
| Find task-specific paths | [Task routes](task-routes.md) |
| Review quality and security constraints | [Quality rules](quality-rules.md) |
| Prepare a pull request, Release, or issue reply | [Templates](templates.md) |
| Understand executable checks | [Guard guide](guard-guide.md) |
| Audit harness gaps | [Scorecard](scorecard.md) and [records](records.md) |

The repository entry point is [`AGENTS.md`](../../AGENTS.md). Machine-readable facts are in [`.harness/manifest.json`](../../.harness/manifest.json). Existing links to the former maintainer guide remain valid through [`docs/maintainers/development-harness.md`](../maintainers/development-harness.md).

## Evidence vocabulary

| Status | Meaning |
|---|---|
| `observed` | Read directly from the current checkout. |
| `declared` | Specified by a build file, workflow, script, or policy, but not necessarily executed in this review. |
| `inferred` | Derived from code relationships and requiring confirmation before a risky change. |
| `unknown` | Not established; stop or record the blocker instead of guessing. |

Command presence is not command success. Pull requests and handoffs must distinguish checks that passed, failed, were blocked, or were skipped.

## Source and freshness

- Harness tier: Standard.
- Audited source commit: `e477f486` on `origin/main`.
- Audit date: 2026-08-25.
- Refresh when modules, build aliases, toolchain declarations, CI, persistence boundaries, release policy, or platform support change.

Run `node scripts/check-harness.mjs` after changing this contract. The check is local and advisory; it is not currently a required GitHub status check.
