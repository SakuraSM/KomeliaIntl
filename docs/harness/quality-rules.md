# Quality rules

## Architecture and scope

- Keep dependencies flowing from applications and UI toward domain contracts and infrastructure implementations. Do not place screen state or Compose types in domain modules.
- Keep platform code in the relevant source set or behind an existing platform interface.
- Do not edit vendored or submodule code for an application-level workaround.
- Separate behavior changes, migrations, dependency updates, and repository policy changes when they can be reviewed independently.
- Preserve backward compatibility for persisted data and public service contracts unless the request explicitly accepts a migration or break.

## Kotlin and Compose

- Put testable cross-platform rules in `commonMain` and their regression tests in `commonTest` when platform APIs are not required.
- Use existing state holders and repositories rather than duplicating data ownership in a Composable.
- Use Compose Resources for user-facing strings and update English and Simplified Chinese together.
- Reuse `KomeliaLayoutSpec`, shared theme and shape tokens, and `KomeliaMotionSpec`; avoid isolated colors, spacing, and durations.
- Keep mobile touch targets at least 48dp and desktop pointer targets at least 40dp.
- Restore focus after overlays, prevent click-through, support Back and Esc, respect safe areas, and honor reduced motion.
- Avoid content-driven card height changes inside the same scan-oriented grid.

## Data, network, and offline behavior

- Treat database migrations, setting enum values, cache formats, and download state as compatibility contracts.
- Make remote/LAN selection deterministic and observable. Do not discard the configured primary address when a LAN probe fails.
- Persist enough failure context for offline logs to explain user-visible errors without exposing credentials or private content.
- Test cancellation, restart, partial files, stale records, and delete behavior for download changes.
- Do not infer an empty remote result when authentication or connectivity failed.

## Readers and navigation

- Keep image, PDF, and EPUB progress semantics stable across platforms.
- Distinguish a tap, drag, swipe, edge gesture, and system Back action; one gesture must not trigger two navigation outcomes.
- Reader chrome must not obscure safe areas or publication content and must remain keyboard accessible where supported.
- Reproduce reported Back behavior with the original stack and event order, not only from a fresh root screen.

## Privacy and security

- Never commit credentials, signing keys, tokens, server addresses, private filenames, account data, or unredacted library media.
- Keep local SDK and signing configuration outside version control.
- Redact screenshots and recordings before adding them to documentation, pull requests, or Releases.
- Use the narrowest external authorization. Reading, pushing, merging, publishing, and closing issues are separate actions.
- Treat GitHub Release assets as public artifacts; inspect their names and contents before upload.

## Review standard

- Review the complete diff, including generated resource changes and deletions.
- A test must assert user-relevant behavior or a stable contract, not merely execute code.
- A successful compile is not a runtime test; an artifact on disk is not an installation test.
- List exact evidence and residual risk. Avoid claims such as “all platforms pass” unless every named platform was actually checked.
