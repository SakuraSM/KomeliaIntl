# Publish a Release and reply to fixed issues

Use the repository templates for every Release and fixed-issue reply. Do not rewrite the structure for each version.

Choose the version with [Version releases](versioning.md) before you prepare the notes.

## Prepare release notes

1. Set the Release title to `Komelia vMAJOR.MINOR.PATCH` for stable releases or `Komelia vMAJOR.MINOR.PATCH-beta.N` for Beta releases. Do not use `Komelia Intl`. Do not omit the `v` or use only the version number.
2. Copy `.github/RELEASE_TEMPLATE.md` to a temporary notes file.
3. Replace every `{{PLACEHOLDER}}`.
4. Put Chinese changes under `## 中文` and English changes under `## English`.
5. Keep the translated lists the same length and order. One Chinese item must map to one English item.
6. Do not repeat the version or project name in the body. Short Release notes do not need category subheadings.
7. Combine closely related fixes into one item. Include an Issue or pull request number when it helps users find details.
8. Keep test commands, internal refactors, omitted platforms, the full changelog, checksums, download lists, and repository attribution out of the Release body.
9. Run the policy test and both checks:

   ```shell
   scripts/test-release-policy.sh
   scripts/check-release-version.sh --tag vX.Y.Z[-PRERELEASE] --previous A.B.C --level LEVEL
   scripts/check-release-notes.sh \
     --file /path/to/release-notes.md \
     --version X.Y.Z[-PRERELEASE] \
     --title "Komelia vX.Y.Z[-PRERELEASE]" \
     --level LEVEL
   ```

   Replace `LEVEL` with `patch`, `minor`, or `major`.

10. Create a draft Release first. Mark Beta versions as pre-releases. Attach and verify the packages before publication. GitHub displays the attached packages outside the Release body.

`.github/RELEASE_TEMPLATE.md` is the canonical format. If a maintainer generates notes with GitHub, remove documentation, dependency maintenance, test details, and other changes that users do not need.

You may correct the title or body of an existing Release to match this format. Do not move its tag, replace its assets, or change its published version.

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
