# Contributing to Komelia Intl

For application changes, start with the [Komelia engineering harness](docs/harness/README.md). It defines branch isolation, diagnosis, module boundaries, task routes, tests, platform builds, pull request review, Releases, and issue replies.

Run the repository-local structural check after changing engineering guidance:

```shell
node scripts/check-harness.mjs
```

Use the repository templates:

- `.github/pull_request_template.md` for pull requests.
- `.github/RELEASE_TEMPLATE.md` for Releases.
- `.github/ISSUE_REPLY_TEMPLATE.md` for fixed-issue updates.

For translation changes, follow [Simplified Chinese contribution guide](docs/i18n/CONTRIBUTING_zh-CN.md) and [Simplified Chinese glossary](docs/i18n/glossary_zh-CN.md).

Do not commit credentials, signing files, server addresses, private content, or unredacted screenshots.
