# Develop and ship a Komelia change

The canonical development contract moved to the [Komelia engineering harness](../harness/README.md). This compatibility entry keeps existing repository and external links valid.

Use these routes:

- [Change protocol](../harness/change-protocol.md) for request intake, isolation, diagnosis, implementation, review, and delivery.
- [Testing guide](../harness/testing-guide.md) for affected-module, platform, UI, and evidence requirements.
- [Task routes](../harness/task-routes.md) for UI, network, offline, persistence, reader, native, localization, upstream, issue, and Release work.
- [Project knowledge](../project-knowledge/README.md) for module ownership and stable flows.
- [Version releases](versioning.md) and [Release and issue replies](release-and-issue-replies.md) for publication.

Run `node scripts/check-harness.mjs` after changing Harness documents. The command validates structure and links; it does not replace application tests or platform builds.
