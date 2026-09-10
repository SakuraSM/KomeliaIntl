# Testing guide

Select tests from impact, not from habit. A green narrow test does not replace an affected platform build or a runtime scenario.

## Baseline checks

Run for every change:

```shell
node scripts/check-harness.mjs
git diff --check
rg -n '^<<<<<<< |^>>>>>>> |^=======$' --glob '!third_party/**' .
```

Run shared UI tests for every application code change:

```shell
./gradlew :komelia-ui:allTests
```

## Impact matrix

| Changed area | Focused checks | Required consuming checks |
|---|---|---|
| Shared Compose UI, resources, or navigation | Relevant `commonTest`, then `:komelia-ui:allTests` | `./gradlew androidDebug desktopJar komfWebUI` |
| Domain behavior | Affected module `allTests` | UI tests plus all consuming applications |
| Database or settings persistence | Migration/repository tests using previous data | UI tests and affected native/Wasm builds |
| Offline downloads or logs | Offline module tests, restart/error-path scenarios | Android and desktop builds; real cache add/read/remove flow |
| Remote/LAN switching | Selection and connectivity state tests | Android connectivity-change flow and at least one non-Android client |
| EPUB package | Package-local checks | Both package builds, then `./gradlew buildEpubReaders` and an EPUB smoke test |
| Android-only | Targeted Android test | `./gradlew androidDebug`, install, launch, and reported flow |
| Desktop-only | Targeted JVM test | `./gradlew desktopJar` and the affected installer task |
| Wasm-only | Targeted Wasm test | `./gradlew komfWebUI` and browser smoke test |
| Native decoder, JNI, WebView, or ONNX | Module tests where available | Platform-native dependency build and consuming package |
| Version or Release policy | `scripts/test-release-policy.sh` | Version and notes checks plus artifact verification |
| Documentation only | Harness check and link review | `git diff --check` |
| Harness checker | `node --test scripts/check-harness.test.mjs` | Harness check and `git diff --check` |

For the EPUB readers, run package commands from their directories:

```shell
cd komelia-epub-reader/ttu-ebook-reader
npm run check
npm run build
```

```shell
cd komelia-epub-reader/komga-webui
npm test
npm run build
```

`komga-webui` has no declared `check` script; its `build` script performs `vue-tsc --noEmit` before Vite.

For local EPUB navigation, run offline `LocalEpubNavigationTest`, `ProgressMarkProgressionActionTest` and SQLite `LocalLibraryManagerIntegrationTest`. Cover EPUB 2/3 nested TOCs, position endpoints, absolute resource URL progress and reindexing an unchanged legacy book without losing locked metadata or saved progress. The package `npm test` covers slow swipes, post-touch momentum, short-chapter non-skipping, pinch/cancel, expiry and cleanup. Repeat the actual touch flow on Android; event-model tests are not device input proof.

## UI and interaction matrix

When rendering or interaction changes, cover the relevant subset:

- Widths: 360dp, 412dp, 600dp, 840dp, and 1280px.
- Themes: Light, Dark, and OLED for color, elevation, or system-bar changes.
- Locales: English and Simplified Chinese; include long translated strings.
- Input: touch, mouse, keyboard, Back, and Esc when navigation or overlays change.
- States: loading, empty, error, long content, missing metadata, offline, and slow network.
- Readers: image, PDF, and EPUB for shared reader/navigation changes.
- Accessibility: focus order, semantic labels, contrast, safe areas, reduced motion, 48dp touch and 40dp pointer targets.

Use real Komga content only when authorized. Redact credentials, addresses, filenames, account data, and private book content before committing evidence.

## Android test-package handoff

When the user will validate on a physical device, follow [Android device validation](android-device-validation.md). Deliver an identifiable debug APK that coexists with production, verify its packaged labels and signature, and keep physical-device acceptance pending until the user reports results.

## Evidence record

In the pull request or handoff, list each executed command and manual scenario with its result. If a required check is blocked, include the reason and the closest substitute; do not silently omit it. Installed and launched packages are stronger evidence than artifact presence alone.

## Image upsampling and local-library cleanup

Run `:komelia-domain:core:jvmTest` for stable tile prefetch bounds while panning. Run `:komelia-infra:image-decoder:shared:allTests` for kernel weights, alpha handling, tile halos, and pixel formats. Run `:komelia-infra:database:sqlite:allTests` for persisted sampling values and local index cleanup, including rollback and actual external file/folder deletion. On Android, compare Lanczos3, Mitchell, and bilinear using synthetic small images; exercise pinch zoom, panning, mode changes, and restart. Record physical-device and panel-detection checks separately from image-renderer checks.

For reader gesture changes, run the JVM Compose `ScalableContainerGestureTest` and repeat maximum zoom followed by both short and wide pinch-in gestures on Android, including while enlarged tiles are rendering. Touch thresholds must use the same coordinate units as pointer positions.

## Reader image lifetime, preloading, and OLED

For chapter navigation, run `LocalFirstBookApiTest`, `ReaderSiblingTest`, `ReaderSiblingStateTest`, and `SiblingStatusContentTest` in UI JVM tests. SQLite `LocalLibraryManagerIntegrationTest` covers imported decimal chapter labels, rescans, and both sibling boundaries. Check partial downloads, online read-list order, query failure/retry, and unchanged current-page progress. Use original reporter data separately from synthetic fixtures.

For Android provider compatibility, run `SafChannelTest` and `AndroidArchiveAccessTest` on a dedicated emulator. These cover seekable and pipe-backed providers, bounded temporary copies, cache eviction, corrupt archives, permissions, free-space limits, and cancellation cleanup. Also import and open a CBZ and EPUB through the real folder picker. Preserve existing app data during package installation.

For archive-format changes, run offline `ArchiveFormatRegressionTest`, `ComicArchiveServiceTest`, and `ArchiveCacheBoundaryTest`, plus Android `AndroidComicArchiveTest`. Cover ZIP/RAR/7z with mismatched suffixes, COPY/LZMA/LZMA2 and solid 7z, reverse reads, persistent-cache reuse, cache pressure, provider timeouts, encryption, invalid paths, CRC errors, and cancellation. UI `ArchiveMessagesTest` checks complete error-resource coverage and wrapped-error presentation.

Optional real-file acceptance stays outside the repository. Set `KOMELIA_QA_ORIGINAL_ARCHIVE` and `KOMELIA_QA_REFERENCE_ARCHIVE` for desktop `OriginalArchiveAcceptanceTest`. On Android, grant the test folder through the app's picker, then supply `qa.originalArchiveUri` and `qa.referenceArchiveUri` as instrumentation arguments to compare all 59 entries in the #67 sample. Without fixtures these acceptance tests are skipped, not passed. Verify actual first/middle/last pages, backwards navigation, re-entry, restart progress and both UI languages separately from byte comparisons.

`PosterGridDensityTest` changes the setting against an actual Compose grid. `LayoutTest` checks density defaults and touch-bound sizing. Repeat slider changes, rotation, and restart on Android; cover local, home, series, book, collection, and read-list grids.

`CardWidthPersistenceTest` delays an earlier save and covers leaving settings during a write. `SettingsStateWrapperTest` verifies serialized updates of different preferences and failed-save state. Domain browser tests use the same Compose foundation runtime version as the consuming UI, with Skiko packaged by the Compose Gradle plugin.

`ReaderTileFallbackTest` exercises the real tile pipeline with delayed full-frame generation: whole-page coverage survives zoom-out, previews are reused across zoom/pan, crop reload replaces preview pixels, and page close retires owned pixels once. `TileFallbackSizeTest` checks the 768-pixel longest-edge limit without upscaling small images. Android `AndroidReaderImageLifetimeTest` also checks real bitmap gap filling and translucent tile alpha; a preview must not be composited beneath already loaded transparent pixels. Use a synthetic high-resolution image for native decoding, rapid zoom/pan, and exit/re-entry checks. ONNX panel detection and reporter-device acceptance remain separate from renderer validation.

`ReaderSystemBarsEffectTest` exercises the Compose reader lifecycle: hide both system bars during reading, restore them while controls are shown, hide again when controls close, and restore on disposal. Repeat with Android gesture navigation, three-button navigation and tablet-sized layouts; the mocked window contract does not prove OEM taskbar behavior.

`RetainedPageLoadTest` in shared UI tests covers navigation cancellation, bounded spread windows, eviction, shutdown, and retry without discarding successful neighboring pages. `ThemeTest` checks OLED background, base surface, and dim surface independently.

`TilingReaderImageLifetimeTest` in shared UI common tests suspends a real domain reader resize while requesting crop reload or shutdown. It checks that native images stay open until the operation finishes and that a shared original/processed image is released only once. These integration tests use the UI module's existing Compose/Skiko test runtime through `:komelia-ui:allTests`.

Run `./gradlew :komelia-app:androidApp:connectedDebugAndroidTest` with only the dedicated test emulator connected for `AndroidReaderImageLifetimeTest`. It verifies that an outgoing frame can still draw a retired Android bitmap. This is separate from the common/JVM tests and does not require a real server or private media.

On Android, use a synthetic multipage CBZ with white borders. Verify crop on/off, fast forward/backward navigation, single/double-page layout, pinch zoom and panning, sampling changes, reader mode changes, and exit/re-entry. Check both process/crash logs and actual page rendering. For OLED, sample unobstructed reader-background pixels; elevated settings panels intentionally retain distinct surface colors. Do not infer physical-panel power behavior from emulator RGB values.

## Server announcements

`AnnouncementsViewModelTest` covers server-content order and fields, empty feeds, request errors, and coroutine cancellation without an update-client dependency. JVM Compose `AnnouncementsContentTest` checks the server notice and empty state without application release sections. For device validation, use an authorized server-admin session to open Server settings / Announcements, then verify App settings / App updates independently. Non-admin accounts cannot open server settings; do not change permissions merely to pass a test.
