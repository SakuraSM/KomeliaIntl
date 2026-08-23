# Publish a Release and reply to fixed issues

Use the repository templates for every Release and fixed-issue reply. Do not rewrite the structure for each version.

## Prepare release notes

1. Copy `.github/RELEASE_TEMPLATE.md` to a temporary notes file.
2. Replace every `{{PLACEHOLDER}}`.
3. Describe every user-visible change in paired Chinese and English entries. Do not list internal refactors unless users need the information to upgrade or troubleshoot.
4. List only the packages attached to this Release. Write `Not included in this Release` for a platform without an asset.
5. Include the SHA-256 digest for each attached package.
6. Link the comparison between the previous tag and the new tag in **Full changelog**.
7. Run both checks:

   ```shell
   scripts/check-release-version.sh --tag vX.Y.Z
   scripts/check-release-notes.sh --file /path/to/release-notes.md --version X.Y.Z
   ```

8. Create a draft Release first. Attach and verify the assets before publication.

GitHub uses `.github/release.yml` when maintainers generate release notes. Its bilingual categories match the headings in `.github/RELEASE_TEMPLATE.md`.

## Reply to a fixed issue

Use `.github/ISSUE_REPLY_TEMPLATE.md`.

- Reply in the language used by the reporter.
- Before publication, link the pull request and keep the issue open.
- After publication, link the Release and list the available packages.
- Close the issue only after the published Release contains the fix and the release artifacts are available.
- If a pull request should close an issue on merge, add `Fixes #NUMBER` to the pull request body. Do not use a closing keyword when the issue must remain open until release verification.

Generate the comment instead of rewriting it manually:

```shell
scripts/render-issue-reply.sh branch zh \
  --branch fix/example \
  --branch-url https://github.com/SakuraSM/KomeliaIntl/tree/fix/example \
  --change '修复离线账号切换' \
  --test 'Android 模拟器回归通过' > /tmp/issue-reply.md

scripts/render-issue-reply.sh implementation zh \
  --pr '#123' \
  --change '修复离线账号切换' \
  --test 'Android 模拟器回归通过' > /tmp/issue-reply.md

scripts/render-issue-reply.sh release en \
  --version 0.19.0 \
  --release-url https://github.com/SakuraSM/KomeliaIntl/releases/tag/v0.19.0 \
  --asset 'Android APK' \
  --test 'APK installation verified' > /tmp/issue-reply.md
```

Review facts and links, then post the generated file. Do not change its headings, paragraph order, or issue-closing rule. Prefer the pull-request form once a PR exists; use the branch form only when an issue update is required before a PR is created.

## GitHub references

- [Automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
- [Linking a pull request to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)
