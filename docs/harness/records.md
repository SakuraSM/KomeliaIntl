# Harness records

This is an append-only record of material harness changes. Record contract changes, not routine application work.

## 2026-08-25 — Standard-tier refresh

- Source: `origin/main` at `e477f486`.
- Mode: refresh of the existing maintainer harness in an isolated worktree.
- Observed: Kotlin Multiplatform/Compose modules, Android/Desktop/Wasm applications, two EPUB packages, Komf extension modules, native submodules, release scripts, and one desktop Release workflow.
- Added: concise entry map, machine-readable manifest, task routes, project knowledge, evidence vocabulary, scorecard, and local structural check.
- Preserved: branch rules, isolated worktrees, regression-first fixes, bilingual resources and Releases, issue closure after verified publication, and existing release scripts/templates.
- Reconciled: `docs/harness/` is canonical; the former maintainer harness path is retained as a compatibility entry.
- Deliberately not changed: branch protection, CI triggers, publication permissions, versions, product code, and platform packages.
- Known gap: `.node-version` declares Node 20 while Release CI declares Node 24. Both are documented; compatibility was not established by this documentation review.
