# Develop and ship a Komelia change

This document defines the repository's development harness. The harness is the set of versioned instructions, tests, scripts, and templates that turns a request into a verified Release. Keep decisions in the repository so another maintainer or coding agent can repeat the work without chat history.

## Start from one source of truth

1. Read the complete issue or request, including attachments and follow-up comments.
2. Write the user-visible failure, the expected behavior, and the affected platforms in the task notes.
3. Separate work that is explicitly deferred. Do not implement it as part of the current change.
4. Check the current default branch, open issues, and existing implementation before choosing a solution.
5. Reply in the reporter's language. Keep a fixed issue open until its Release and assets are published and verified.

## Isolate the work

1. Fetch `origin` and create a branch from the current `origin/main`.
2. Use `fix/`, `feat/`, or `docs/`. Do not use a `codex/` prefix.
3. Create a separate worktree when another task has uncommitted changes. One worktree owns one task.
4. Record the starting commit. Run `git status --short --branch` before editing.
5. Preserve user changes, credentials, signing files, and local SDK configuration. Never add them to a commit or tool output.

## Reproduce before editing

1. Follow the shortest path that demonstrates the problem.
2. Save the exact state transition, screen size, theme, locale, input method, and content type that matter.
3. Trace the state owner, repository, platform implementation, and UI caller. Do not patch only the visible symptom.
4. List plausible causes and use code or runtime evidence to reject them.
5. If runtime verification is blocked, state the blocker. Do not report an unverified scenario as passed.

For UI work, inspect the current screen at Compact, Medium, and Expanded widths. Include Light, Dark, and OLED when colors or elevation change. Test touch, mouse, keyboard, Back, and Esc when navigation or overlays change.

## Plan the smallest complete change

The plan must define these facts:

- Scope and explicit non-goals.
- User-visible acceptance criteria.
- Changed modules and platforms.
- Stored-data, API, navigation, localization, accessibility, and release risks.
- Automated tests and manual scenarios.
- Required packages, if the change will ship in a Release.

Prefer existing repositories, state holders, design tokens, and navigation types. Add a shared abstraction only when two callers need the same rule or when a pure function makes a regression test possible.

## Add the failing test

Add a test before the production fix when the behavior can be isolated.

- Put pure state, layout, parsing, and navigation rules in `commonTest`.
- Test repository behavior against its public interface.
- Test a database migration with data from the previous schema.
- Test a reported defect with the original event order and boundary values.
- Add screenshot or device tests when correctness depends on rendering, focus, system insets, or gestures.

Run the new test once before implementation. Confirm that it fails for the expected reason. A compilation failure caused only by the not-yet-added API is acceptable for a new type. Record that distinction in the pull request.

## Implement within the existing boundaries

- Keep business rules in the domain layer, persistence in infrastructure, and presentation state in the UI layer.
- Keep platform code behind `expect` and `actual` or an existing platform interface.
- Preserve Komga API contracts, stored values, and database compatibility unless the task explicitly changes them.
- Put user-facing text in Compose Resources. Update English and Simplified Chinese together.
- Use `KomeliaLayoutSpec`, theme tokens, shared components, and `KomeliaMotionSpec`. Do not add isolated spacing, colors, or durations when a token exists.
- Keep mobile touch targets at least 48dp and desktop targets at least 40dp.
- Restore focus after a dialog closes. Block clicks through modal content. Support Back and Esc.
- Respect reduced motion and system safe areas.
- Never write credentials, server addresses, private filenames, or unredacted screenshots to the repository.

## Verify in layers

Run the narrowest useful check after each edit. Run the full required set before the pull request.

### Checks for every change

```shell
git diff --check
rg -n '^<<<<<<< |^>>>>>>> |^=======$' --glob '!third_party/**' .
```

Run the shared UI tests for every code change:

```shell
./gradlew :komelia-ui:allTests
```

### Checks by affected area

| Area | Required checks |
|---|---|
| Shared Compose UI or navigation | `./gradlew :androidDebug :desktopJar :komfWebUI` |
| Domain or persistence | Affected module `allTests`, `:komelia-ui:allTests`, and all consuming platform builds |
| EPUB reader | Run `npm run check` and `npm run build` in each changed EPUB package, then `./gradlew buildEpubReaders` |
| Android-only code | `./gradlew :androidDebug`; install and launch the APK on an emulator or device |
| Desktop-only code | `./gradlew :desktopJar` and the affected installer task |
| Wasm-only code | `./gradlew :komfWebUI` and a browser smoke test |
| Version or Release policy | `scripts/test-release-policy.sh`, `scripts/check-release-version.sh`, and `scripts/check-release-notes.sh` |
| Documentation only | `git diff --check` and a link and command review |

If memory pressure blocks a Wasm build, increase the Gradle and Kotlin daemon heaps for that command. Record the environment failure separately from source failures.

### Manual UI matrix

Use real Komga content when the task affects browsing or reading. Cover at least these cases:

- 360dp and 412dp phones.
- 600dp and 840dp adaptive layouts.
- A 1280px desktop or browser window.
- English and Simplified Chinese.
- Long titles, missing metadata, empty data, loading, and errors.
- The exact navigation or gesture sequence from the report.

Redact account data, server addresses, filenames, and private book content before committing screenshots or recordings.

## Review before pushing

1. Read the complete diff. Check behavior, tests, localization, accessibility, and cleanup paths.
2. Run `git diff --check` again.
3. Confirm that `git status --short` lists only files for this task.
4. Split commits by reversible concern when the change contains independent steps.
5. Push the task branch. Do not rewrite shared history.

## Open and review the pull request

Use `.github/pull_request_template.md`.

1. Describe the user-visible result, not the editing process.
2. Link the issue without a closing keyword when closure must wait for a Release.
3. List only checks that actually ran. State blocked manual scenarios.
4. Review the diff again after automated checks or review changes.
5. Merge only when the acceptance criteria pass and required checks have no unresolved failures.

## Publish the Release

Follow [Version releases](versioning.md) and [Publish a Release and reply to fixed issues](release-and-issue-replies.md).

1. Choose the highest SemVer level required by the included changes.
2. Update every application version source with `scripts/build-release.sh`.
3. Name the GitHub Release `Komelia vMAJOR.MINOR.PATCH`.
4. Copy `.github/RELEASE_TEMPLATE.md`.
5. Put Chinese items under `## 中文` and English items under `## English`.
6. Keep both lists in the same order.
7. Keep the Release body limited to user-visible changes. GitHub displays attached assets separately.
8. Create a draft Release. Build, attach, and verify only the packages in scope.
9. Publish after the tag, application version, title, language sections, and attached assets agree.
10. Install or launch each attached package before marking it verified.
11. Reply to fixed issues with `.github/ISSUE_REPLY_TEMPLATE.md`, then close them.

Do not put test commands, implementation details, missing-platform notices, a full commit list, or repository attribution in the Release body. Keep those facts in the pull request and maintainer documentation.

## Improve the harness after a miss

When the same mistake happens twice, add an executable check or regression test. Update this document only when a command, boundary, or required decision changes. Prefer a script that fails over a paragraph that asks maintainers to remember.

## References

- [OpenAI Harness engineering](https://openai.com/index/harness-engineering/): isolated worktrees, repository-local context, and executable feedback loops.
- [GitHub repository best practices](https://docs.github.com/en/repositories/creating-and-managing-repositories/best-practices-for-repositories): protected branches, review, and repository documentation.
- [GitHub pull request templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/creating-a-pull-request-template-for-your-repository): consistent change and verification records.
- [Android adaptive app quality guidelines](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality): form-factor and continuity tests.
- [Now in Android architecture](https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md): clear module boundaries and testable interfaces.
- [Readest release workflow](https://github.com/readest/readest/blob/main/.github/workflows/release.yml): automated multi-platform packages attached to one Release.
- [Semantic Versioning 2.0.0](https://semver.org/): version selection and immutable published versions.
