# Testing guide

The reusable [regression suite](../../tests/regression/README.md) defines profiles, case IDs, commands, and acceptance records. Start with `node scripts/regression.mjs plan core`. A green command result does not complete pending device cases. Use the detailed routes below to expand the selected profile when a change affects additional consumers.

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

## Desktop startup

Run the installer smoke scripts in the desktop workflow. Require both a visible window and the
application-initialization log event, then inspect the screenshot. A native-library or keyring
failure can open an error window while the process stays alive.

Linux verification needs WebKitGTK, a desktop-menu directory, and a D-Bus session with a test
keyring. After changing native-library loading, verify initialization with the system WebView
loaded first and exercise the bundled image decoder. Preserve the decoder's bundled GLib path
when the WebView is unavailable. Keep container reproduction separate from the final CI package.
`scripts/verify-linux-installer.sh` prepares the Linux fixture, installs the DEB, runs the native
probes, and delegates GUI capture to `scripts/desktop-smoke-linux.sh`. Its package lookup uses
the system `grep`; the runner does not need ripgrep installed.
`scripts/DesktopNativeSmoke.java` runs against the installed jars, calls the production library
initializer, then decodes and resizes a synthetic PNG and checks every RGBA pixel. Linux runs
both `system-webview` and `bundled` modes in separate processes; Windows runs `bundled` mode.
The same probe constructs a lookup-table operation and checks PNG/JPEG thumbnail decoding.
The lookup-table graph reproduces GLib sorting-symbol failures missed by a single-input resize.
The Linux native build checks all GLib symbols required by VIPS and the JNI libraries against
the system compilation baseline before packaging.

`LoginContentLayoutTest` measures the real login form at desktop and phone widths. Keep the
maximum width before `fillMaxWidth` so an incoming full-width constraint cannot defeat it.

Windows verification enumerates visible windows owned by the launched application process tree.
`Process.MainWindowHandle` alone can miss a window owned by a child process or another native
window. On failure, retain the scoped process/window inventory and a runner screenshot so a
window-discovery failure can be distinguished from application initialization or rendering errors.

## Image upsampling and local-library cleanup

For global color correction, run core `EffectiveColorCorrectionTest`, SQLite `DefaultColorCorrectionIntegrationTest`, UI JVM `DefaultColorCorrectionContentTest`, `DefaultColorCorrectionStateTest`, `GlobalColorProcessingTest`, and the UI Wasm `ColorCorrectionBrowserStorageTest`. These cover default-off, legacy overrides, inheritance, explicit off, identity overrides, transaction rollback, save cancellation/failure, live lookup-table changes, grayscale ownership, IndexedDB reopen and old browser settings JSON.

Run `COLOR-01` on the final debug APK. Create a preset through the book editor, select it as the global default, compare inherited and custom books, disable correction for one book, return to inheritance, restart and verify settings and reading position. Capture the actual rendered difference, not just the selected label. Repeat the new controls in English and Simplified Chinese. An unavailable device or system image stays pending.

`ColorCorrectionScreenIdentityTest` covers isolation between books and repeated entries, Activity-state serialization and legacy identity restoration. Native acceptance must open A's editor, close it, then open B's editor in the same app session; cold-start-only testing misses retained-model bugs.

Run `:komelia-domain:core:jvmTest` for stable tile prefetch bounds while panning. Run `:komelia-infra:image-decoder:shared:allTests` for kernel weights, alpha handling, tile halos, and pixel formats. Run `:komelia-infra:database:sqlite:allTests` for persisted sampling values and local index cleanup, including rollback and actual external file/folder deletion. On Android, compare Lanczos3, Mitchell, and bilinear using synthetic small images; exercise pinch zoom, panning, mode changes, and restart. Record physical-device and panel-detection checks separately from image-renderer checks.

For reader gesture changes, run the JVM Compose `ScalableContainerGestureTest` and repeat maximum zoom followed by both short and wide pinch-in gestures on Android, including while enlarged tiles are rendering. Touch thresholds must use the same coordinate units as pointer positions.

## Reader image lifetime, preloading, and OLED

`ReaderModeHintEffectTest` covers initialized first-entry hints, composition restoration, explicit mode/direction requests, and a new reading session. Run `READER-HINT-01` on API35 and API37: ten portrait/landscape cycles must preserve the page without replaying the mode hint. Duplicate selections must still save preferences even though they do not show another hint.

`ContinuousViewportTest` bounds the first visible long-page request to the scroll-axis viewport, including RTL right-edge coordinates and the first image after a header. `CONTINUOUS-01` requires actual scrolling, rotation, renderer logs and screenshots. Original-panel acceptance remains separate; a continuous-reader improvement does not close all of #62.

`ContinuousViewportChangesTest` mounts a real Compose lazy list and checks equal-offset page changes, remeasurement without scrolling and unchanged-viewport deduplication. Compare the original long-image fixture on the old and final APK, keeping sampling settings identical. Record transient low-resolution previews separately from persistent blur after settling.

Core `VisibleImageRequestsTest` checks final-viewport conflation, A/B/A version isolation, duplicate geometry and explicit reloads. JVM `VisibleImageRequestsConcurrencyTest` submits 200,000 concurrent requests. UI `ReaderTileFallbackTest` also suspends obsolete native tile work and requires sharp tiles for the final viewport, not merely whole-page preview coverage. Repeat the original fast page-jump and back-and-forth scrolling sequence on the physical device; queue tests do not establish device acceptance.

For Android page sharing, run UI `ReaderPageShareStateTest` and `PageShareButtonTest`, then device `AndroidPageShareTest`. These cover the clicked page and unchanged bytes, repeated clicks, fetch failure/retry, cancellation, accessible busy state, image MIME and the provider's narrow read-only URI. Run `SHARE-01` on the final APK and cancel the system chooser without sending. Local-file coverage does not prove online-server availability or a specific receiving app.

For chapter navigation, run `LocalFirstBookApiTest`, `ReaderSiblingTest`, `ReaderSiblingStateTest`, and `SiblingStatusContentTest` in UI JVM tests. SQLite `LocalLibraryManagerIntegrationTest` covers imported decimal chapter labels, rescans, and both sibling boundaries. Check partial downloads, online read-list order, query failure/retry, and unchanged current-page progress. Use original reporter data separately from synthetic fixtures.

For Android provider compatibility, run `SafChannelTest` and `AndroidArchiveAccessTest` on a dedicated emulator. These cover seekable and pipe-backed providers, bounded temporary copies, cache eviction, corrupt archives, permissions, free-space limits, and cancellation cleanup. Also import and open a CBZ and EPUB through the real folder picker. Preserve existing app data during package installation.

For local-series navigation, SQLite `LocalLibraryManagerIntegrationTest` checks local single-book and multi-book series with a non-root server user selected, including pagination, read-status filtering, current-user progress and exclusion of other servers' downloads. `OneshotEmptySeriesTest` checks empty results, request failures, manual retry and series-event reload. Repeat a local series-card click on Android with the same selected user; opening a book directly or testing only root misses this path.

For archive-format changes, run offline `ArchiveFormatRegressionTest`, `ComicArchiveServiceTest`, and `ArchiveCacheBoundaryTest`, plus Android `AndroidComicArchiveTest`. Cover ZIP/RAR/7z with mismatched suffixes, COPY/LZMA/LZMA2 and solid 7z, reverse reads, persistent-cache reuse, cache pressure, provider timeouts, encryption, invalid paths, CRC errors, and cancellation. UI `ArchiveMessagesTest` checks complete error-resource coverage and wrapped-error presentation.

Optional real-file acceptance stays outside the repository. Set `KOMELIA_QA_ORIGINAL_ARCHIVE` and `KOMELIA_QA_REFERENCE_ARCHIVE` for desktop `OriginalArchiveAcceptanceTest`. On Android, grant the test folder through the app's picker, then supply `qa.originalArchiveUri` and `qa.referenceArchiveUri` as instrumentation arguments to compare all 59 entries in the #67 sample. Without fixtures these acceptance tests are skipped, not passed. Verify actual first/middle/last pages, backwards navigation, re-entry, restart progress and both UI languages separately from byte comparisons.

`PosterGridDensityTest` changes the setting against an actual Compose grid. `LayoutTest` checks density defaults and touch-bound sizing. Repeat slider changes, rotation, and restart on Android; cover local, home, series, book, collection, and read-list grids.

`CardWidthPersistenceTest` delays an earlier save and covers leaving settings during a write. `SettingsStateWrapperTest` verifies serialized updates of different preferences and failed-save state. Domain browser tests use the same Compose foundation runtime version as the consuming UI, with Skiko packaged by the Compose Gradle plugin.

`ReaderTileFallbackTest` exercises the real tile pipeline with delayed full-frame generation: whole-page coverage survives zoom-out, previews are reused across zoom/pan, crop reload replaces preview pixels, and page close retires owned pixels once. `TileFallbackSizeTest` checks the 768-pixel longest-edge limit without upscaling small images. Android `AndroidReaderImageLifetimeTest` also checks real bitmap gap filling and translucent tile alpha; a preview must not be composited beneath already loaded transparent pixels. Use a synthetic high-resolution image for native decoding, rapid zoom/pan, and exit/re-entry checks. ONNX panel detection and reporter-device acceptance remain separate from renderer validation.

`ReaderSystemBarsEffectTest` exercises the Compose reader lifecycle: hide both system bars during reading, restore them while controls are shown, hide again when controls close, and restore on disposal. Repeat with Android gesture navigation, three-button navigation and tablet-sized layouts; the mocked window contract does not prove OEM taskbar behavior.

`RetainedPageLoadTest` in shared UI tests covers navigation cancellation, bounded spread windows, eviction, shutdown, and retry without discarding successful neighboring pages. `ThemeTest` checks OLED background, base surface, and dim surface independently.

For PANELS mode, run `PanelLoadLifetimeTest`, `PanelViewportTest`, `ReaderPanelPrefetchTest`, and `PanelPrerenderSettingsTest` through UI tests. These cover in-flight page reuse, shutdown cleanup, bounded lookahead, the whole-page transition, shared pixel budgets, foreground priority, raster-equivalent cache keys, and the actual setting controls. SQLite `PanelPrerenderSettingsIntegrationTest` covers migration from version 15, JSON defaults, and reopening all supported counts. On Android, use a synthetic multipage comic with the actual panel detector, test counts 0/1/2, cross-page navigation, sampling changes, exit/re-entry, and restart persistence. Confirm `reused pre-rendered viewport` in debug logs independently of model-detection completion. Original reporter files and physical-device acceptance remain separate checks.

`TilingReaderImageLifetimeTest` in shared UI common tests suspends a real domain reader resize while requesting crop reload or shutdown. It checks that native images stay open until the operation finishes and that a shared original/processed image is released only once. These integration tests use the UI module's existing Compose/Skiko test runtime through `:komelia-ui:allTests`.

Run `./gradlew :komelia-app:androidApp:connectedDebugAndroidTest` with only the dedicated test emulator connected for `AndroidReaderImageLifetimeTest`. It verifies that an outgoing frame can still draw a retired Android bitmap. This is separate from the common/JVM tests and does not require a real server or private media.

On Android, use a synthetic multipage CBZ with white borders. Verify crop on/off, fast forward/backward navigation, single/double-page layout, pinch zoom and panning, sampling changes, reader mode changes, and exit/re-entry. Check both process/crash logs and actual page rendering. For OLED, sample unobstructed reader-background pixels; elevated settings panels intentionally retain distinct surface colors. Do not infer physical-panel power behavior from emulator RGB values.

## Login readiness and EPUB sibling state

`MainScreenIdentityTest` checks that login sessions have different navigation keys while serialization retains the current key. `SessionNavigationLifetimeTest` mounts real nested Voyager navigators, logs out, and opens another server with the same library screen key. The old library model must be disposed. Repeat A/B/A/B on Android and verify both the selected library and the all-libraries contents.

`ReaderSiblingStateTest.reopeningUsesSavedProgressInsteadOfTheDetailScreensSnapshot` passes an unread navigation snapshot while the content API contains page 2. Reopening must restore page 2. Use a non-final page for device acceptance; completed books intentionally reopen at their beginning.

`BookProgressRefreshTest` checks initial detail entry and return from a reader with no progress event. Both must refresh saved progress without adding duplicate event subscriptions. On-device acceptance must check both the restored reader page and the percentage displayed on the retained detail screen.

TTU's `npm run test:browser` compiles the production styles and checks document scroll ownership with real wheel input. It requires Playwright resolvable by Node and a compatible browser, supplied through the QA environment's `NODE_PATH` and `CHROME_BIN` when needed. Missing browser tooling is blocked, not passed. Follow this focused regression with actual EPUB scrolling, bookmark save, Back and reopen on the final APK.

`OnlineLoginCoordinatorTest`, `LoginViewModelTest`, `LoginHttpRetryTest`, and `DefaultServerUrlResolverTest` cover route readiness, time budgets, retry classification, cancellation, and stale results. Run UI, domain-core, and shared-app `allTests`. The shared-app Wasm tests package Skiko through the Compose plugin, as UI and domain-core tests do.

`komga-webui` tests cover sibling lookup failures, independent directions, retries, stale responses, disposal, and self-references. The eight chapter-scroll gesture tests remain separate. On a dedicated Android emulator, use a controlled local service for cold login, restore, timeout and cancellation, then cross-book EPUB failure and retry. Real accounts and daily application data are outside this fixture.

`EpubReaderSessionTest` covers bridge requests after close, delayed results, and a fresh session on re-entry. Native acceptance must start a delayed sibling retry, confirm the request reached the service, press Back, reopen the same book before the response arrives, and verify that no late request loads another book. A standalone JavaScript disposal test cannot prove that native exit actually retires the document. Wait for the WebView's visual-state callback before injected touch tests and assert trusted touch delivery, including two simultaneous pointers for pinch cases.

## Server announcements

`AnnouncementsViewModelTest` covers server-content order and fields, empty feeds, request errors, and coroutine cancellation without an update-client dependency. JVM Compose `AnnouncementsContentTest` checks the server notice and empty state without application release sections. For device validation, use an authorized server-admin session to open Server settings / Announcements, then verify App settings / App updates independently. Non-admin accounts cannot open server settings; do not change permissions merely to pass a test.

## Surface material and EPUB implementation selection

Run `APPEARANCE-01` through `APPEARANCE-03` and `EPUB-IMPL-01` from the regression catalog. `SurfaceStyleIntegrationTest` covers V17/V18 to V19 migration, legacy checksums, fresh data and reopen. `SurfaceStyleBrowserStorageTest` covers legacy browser JSON and persistence. `AppearancePreferenceTest` covers rapid choices, failure/retry, obsolete failures and writes accepted before leaving settings.

`KomeliaPageScaffoldTest` mounts real Compose layout with an empty toolbar and verifies identical first-item position/scroll range across materials. `GlassScrollBudgetTest` checks overlapping scroll owners, the settle interval and disposal. `ReducedMaterialMotionTest`, `DropdownChoiceFocusTest`, and `SettingsScreenLayoutTest` cover material motion, anchor focus and responsive layout. `GlassContrastTest` bounds neutral backdrop tones; it does not replace rendered contrast inspection on every device.

For runtime performance, prewarm both modes on the same APK/device/content and keep all raw repetitions. Report P95 and modern/legacy Android jank counters separately; do not select the best repetition or substitute emulator statistics for the physical-device gate. Record idle rendered-frame counts with reading controls hidden and memory before/after repeated entries. Run native accessibility with TalkBack enabled; UI hierarchy inspection alone is not screen-reader operation.

`PopupSemanticsBrowserTest` mounts a real browser viewport and requires the main accessibility tree
to keep updating after repeated popup and dialog dismissal. `ImageLoadingBrowserTest` exercises
Coil decoding against the selected Web Skiko runtime. Run `:komelia-ui:wasmJsBrowserTest` after any
Web runtime change. Its Karma configuration enables software WebGL and allows real frame waits;
without a rendering context, a viewport can exist without producing semantics. A visible popup
closing or a JVM focus test does not establish a working browser accessibility tree. The dependency
boundary and actual browser evidence are recorded in [the verification report](../verification/liquid-glass-20261022.md).

`PosterGridDensityTest.tabletGridDoesNotStretchTheDefaultCoverToHalfThePage` measures a real 740dp grid beside a tablet navigation rail. `CoverCaptionFontScaleTest` requires a complete supporting line at 130% font scale. `BottomBulkActionsLayoutTest` requires the inline actions to remain below content and above the reserved navigation area. Repeat both 840dp default density and 360dp large-font captions on the final APK.

`ReaderKeyboardFocusEffectTest` mounts a delayed page key target and requires the first navigation key to reach it. Focus must be requested only after the viewport is measured; a wrapper that changes an early return can otherwise request focus before the target exists. `SearchGlassSamplingTest` checks input-focus sampling pauses. All five main destinations must retain the same selected navigation material. On a dedicated emulator, compare the bars over both plain and illustrated content, inspect capture pauses during scrolling and editing, and recheck Light, Dark, OLED and reduced transparency. `GlassContrastTest` checks both text contrast and visible backdrop variation; GPU rendering still needs the runtime check.
