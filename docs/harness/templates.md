# Delivery templates

Use the repository-owned templates rather than recreating formats in task notes.

## Pull request

Start from [`.github/pull_request_template.md`](../../.github/pull_request_template.md). Keep these facts explicit:

- User-visible result in Chinese and English when the change is user-facing.
- Related issue without an automatic closing keyword when closure waits for a Release.
- SemVer impact: none, patch, minor, or major.
- Checks that actually ran and their target environments.
- Required checks that were blocked or intentionally out of scope.
- Screenshots or recordings only when redacted.

## Release

Use [`.github/RELEASE_TEMPLATE.md`](../../.github/RELEASE_TEMPLATE.md), [versioning rules](../maintainers/versioning.md), and the [publication procedure](../maintainers/release-and-issue-replies.md).

- Title: `Komelia v<MAJOR.MINOR.PATCH>`.
- Body: one `## 中文` module followed by one `## English` module.
- Keep translated items in the same order.
- Include user-visible features and fixes; summarize unrelated maintenance.
- Exclude test logs, implementation details, absent-platform commentary, commit dumps, and repository attribution.
- Treat the Release page Assets list as the package source of truth.

## Issue reply

Use [`.github/ISSUE_REPLY_TEMPLATE.md`](../../.github/ISSUE_REPLY_TEMPLATE.md) or `scripts/render-issue-reply.sh`.

- Reply in the reporter's language.
- Before publication, state that the fix is merged or planned and keep the issue open.
- After the Release and relevant assets are published and verified, include the version, link, and concise verification result, then close the issue.
- Do not claim that an issue is released based only on a merged pull request.

## Handoff

Use this compact order:

1. Outcome and user-visible behavior.
2. Changed boundaries or important files.
3. Tests and manual verification, with exact status.
4. Blockers, residual risk, and explicit non-goals.
5. External actions completed or still requiring authorization.
