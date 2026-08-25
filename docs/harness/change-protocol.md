# Change protocol

## 1. Establish the request

1. Read the complete request, issue, attachments, and follow-up comments.
2. Write the user-visible failure or outcome, affected platforms, and acceptance criteria.
3. Separate explicit non-goals and deferred work.
4. Inspect the current default branch and implementation before choosing a solution.
5. Record evidence as observed, declared, inferred, or unknown.

## 2. Isolate the work

1. Fetch `origin` and branch from the current `origin/main` unless the task names another base.
2. Follow repository naming such as `fix/`, `feat/`, or `docs/`; never add a `codex/` prefix.
3. Use one worktree per task. Do not mix HarmonyOS, Compose, Release, or unrelated platform work.
4. Record the start commit and inspect `git status --short --branch` before editing.
5. Preserve user changes and machine-local configuration.

## 3. Diagnose before editing

1. Reproduce the shortest failing path when practical.
2. Capture the event order, state, target, window size, locale, theme, input method, and content type that matter.
3. Trace the state owner, domain operation, persistence or network boundary, and UI caller.
4. Reject plausible causes with code or runtime evidence.
5. If verification is blocked, report the blocker rather than converting an assumption into a pass.

For a testable defect, add a regression test that fails for the expected reason before changing production code. A compile failure caused only by a not-yet-added API is not the same as a behavioral regression.

## 4. Plan the smallest complete change

Use the [task routes](task-routes.md) and [context boundaries](../project-knowledge/contexts.md). The plan must identify:

- Scope and non-goals.
- User-visible acceptance criteria.
- Touched modules and target platforms.
- API, persistence, navigation, localization, accessibility, privacy, and Release risks.
- Focused tests, required platform builds, and manual scenarios.
- Any external action such as pushing, opening a pull request, merging, publishing, or closing an issue.

External actions require explicit authorization. Implementation permission alone does not authorize publication.

## 5. Implement within boundaries

- Keep business behavior in domain modules, persistence and native integration in infrastructure modules, and presentation state in `komelia-ui`.
- Put platform behavior behind the existing source-set boundary or interface.
- Prefer existing repositories, state holders, design tokens, resources, and navigation types.
- Add a shared abstraction only for a shared rule or a testable pure behavior.
- Keep English and Simplified Chinese resources synchronized.
- Preserve stored values, migrations, Komga contracts, reader progress, and package identity unless the task explicitly changes them.
- Never add credentials, private server data, local paths, private filenames, or unredacted media.

## 6. Verify in layers

Run the narrowest useful test while editing, then the complete affected-area route from the [testing guide](testing-guide.md). Record:

- Exact command or scenario.
- Result: passed, failed, blocked, or skipped.
- Relevant environment and target.
- For failures, whether the cause is source, environment, dependency resolution, or unavailable hardware/data.

Do not use file existence, compilation alone, or a local unit test as proof of runtime, package, or online Release readiness.

## 7. Review and deliver

1. Read the full diff for behavior, tests, localization, accessibility, privacy, and cleanup paths.
2. Run `git diff --check`, the harness check, and the affected checks again.
3. Confirm the worktree contains only task-owned files.
4. Split independent concerns into reversible commits.
5. Use the repository pull request template and list only checks that actually ran.
6. Merge, publish, or close issues only when explicitly requested and when their acceptance gates are met.

Release work additionally follows [versioning](../maintainers/versioning.md) and [Release and issue replies](../maintainers/release-and-issue-replies.md).

## 8. Improve the feedback loop

When a class of mistake repeats, add the smallest regression test or executable guard that would have stopped it. Update project knowledge only when responsibilities, entry points, contracts, or verification routes change.
