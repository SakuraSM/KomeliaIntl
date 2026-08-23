# Issue reply templates

Reply in the language used by the reporter. Keep the issue open until the fix is available in a published Release.
Generate replies with `scripts/render-issue-reply.sh`; the blocks below document the exact generated structure.

## Development branch update

Use this reply when a fix is available on a pushed branch but no pull request exists yet.

```markdown
已在分支 [`{{BRANCH}}`]({{BRANCH_URL}}) 完成修复，等待合并和发布。

改动：

- {{CHANGE_1}}
- {{CHANGE_2}}

验证：

- {{TEST_1}}
- {{TEST_2}}

该修复尚未合并或发布。Issue 将保持开启，待包含此修复的 Release 发布并验证后关闭。
```

```markdown
The fix is available on branch [`{{BRANCH}}`]({{BRANCH_URL}}) and is awaiting merge and release.

Changes:

- {{CHANGE_1}}
- {{CHANGE_2}}

Verification:

- {{TEST_1}}
- {{TEST_2}}

This fix has not been merged or released yet. The issue will remain open until a published Release includes the fix and the release is verified.
```

## Pull request update

Use this reply after the pull request is ready or merged but before the Release is published.

```markdown
已在 PR #{{PR_NUMBER}} 修复。

改动：

- {{CHANGE_1}}
- {{CHANGE_2}}

验证：

- {{TEST_1}}
- {{TEST_2}}

该修复尚未发布。Issue 将保持开启，待包含此修复的 Release 发布并验证后关闭。
```

```markdown
Fixed in PR #{{PR_NUMBER}}.

Changes:

- {{CHANGE_1}}
- {{CHANGE_2}}

Verification:

- {{TEST_1}}
- {{TEST_2}}

This fix has not been released yet. The issue will remain open until a published Release includes the fix and the release is verified.
```

## Release update

Use this reply after the Release is published and its assets are available.

```markdown
修复已随 [Komelia v{{VERSION}}]({{RELEASE_URL}}) 发布。

可用安装包：

- {{ASSET_1}}
- {{ASSET_2}}

已验证：

- {{RELEASE_VERIFICATION_1}}
- {{RELEASE_VERIFICATION_2}}

如果该版本仍可复现，请附上复现步骤、平台和错误日志，我们会重新打开此 Issue。
```

```markdown
The fix is available in [Komelia v{{VERSION}}]({{RELEASE_URL}}).

Available packages:

- {{ASSET_1}}
- {{ASSET_2}}

Verified:

- {{RELEASE_VERIFICATION_1}}
- {{RELEASE_VERIFICATION_2}}

If the issue still occurs in this version, add the reproduction steps, platform, and error log. We will reopen the issue.
```
