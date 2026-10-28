# Development map

## Design references

[Versioned design concepts](../design/README.md) record selected visual directions, Screen mappings, and implementation boundaries. Generated images are design targets, not runtime or performance evidence.

## Product surfaces

| Surface | Entry area | Main dependencies | Build alias |
|---|---|---|---|
| Android app | `komelia-app/androidApp` | shared app, UI, domain, offline, SQLite, WebView, ONNX | `androidDebug`, `androidRelease` |
| Desktop app | `komelia-app/desktopApp` | shared app, UI, domain, offline, SQLite, VIPS, WebView, ONNX | `desktopRun`, `desktopJar`, installer aliases |
| Wasm app | `komelia-app/webApp` | shared app, UI, domain, offline, Wasm database/decoder, WebView | `komfWebUI` |
| Shared composition root | `komelia-app/shared` | UI, domain, offline, database transaction, WebView | consumed by applications |
| Komf browser extension | `komelia-komf-extension` | extension app/background/content/popup/shared modules | `komfExtensionChrome`, `komfExtensionFirefox` |
| EPUB readers | `komelia-epub-reader/komga-webui`, `komelia-epub-reader/ttu-ebook-reader` | Vue/Vite and Svelte/Vite packages embedded as app resources | `buildEpubReaders` |

## Core modules

| Area | Responsibility | Must not own |
|---|---|---|
| `komelia-ui` | Compose screens, presentation state, navigation, shared resources, design system, reader UI | database implementation or platform package wiring |
| `komelia-domain/core` | application behavior, repositories/contracts, Komga-facing orchestration | Compose UI |
| `komelia-domain/komga-api` | Komga API models and client boundary | screen state or storage implementation |
| `komelia-domain/offline` | download/cache behavior, local-folder indexing, offline APIs, and platform content extraction | platform UI |
| `komelia-infra/database/*` | transactions and SQLite/Wasm persistence implementations | user interaction policy |
| `komelia-infra/image-decoder/*` | shared decoder contract and VIPS/Wasm implementations | reader navigation state |
| `komelia-infra/webview` | platform WebView bridge used by EPUB and web content | publication business rules |
| `komelia-infra/onnxruntime/*` | image enhancement API and JVM implementation | UI preference ownership |
| `komelia-infra/jni` | native resource loading and bindings | domain workflows |
| `third_party/*` | vendored/submodule dependencies | fork-specific application fixes |

## Dependency direction

Applications assemble platform implementations and depend on shared app/UI/domain modules. UI consumes domain contracts and selected infrastructure abstractions. Domain code may expose infrastructure contracts used for transactions, decoding, and inference, but must remain free of Compose presentation state. Platform implementations stay in Android, JVM, or Wasm source sets.

Some Gradle project dependencies are not a textbook layered graph because shared interfaces and implementations are split across modules. Before moving a type, inspect every `build.gradle.kts` consumer and source-set actual implementation rather than relying only on directory names.

Local-folder libraries enter through `komelia-ui/.../settings/local`, are orchestrated by `komelia-domain/offline/.../local/LocalLibraryManager`, and reuse the offline SQLite repositories and reader APIs. Android storage access and hourly background work live in the offline Android source set; JVM folder/archive implementations live in the JVM source set; Wasm exposes the unsupported boundary explicitly.

## Optional MangaBaka metadata

Series-facing application APIs return `KomeliaSeries`, which preserves the Komga series fields and adds nullable MangaBaka metadata. Remote series and collection queries enrich their results only when Komf and MangaBaka are enabled. `MangaBakaMetadataLoader` limits optional requests to three seconds, preserves cancellation, and reports an unavailable-metadata notice while retaining the base catalog. Local and offline series remain independent of Komf and return no MangaBaka enrichment.

The settings flag is stored by `KomfSettingsRepository`; fork migration V18 adds it without changing published V1–V17 migrations. Series detail, linking dialogs and Komf settings live in the shared UI's `mangabaka` packages. Treat fetched titles, descriptions and tags as catalog content; application controls use synchronized English and Simplified Chinese resources.

## Build and dependency surfaces

- `settings.gradle.kts` is the module registry and repository policy.
- `gradle/libs.versions.toml` is the dependency catalog.
- Root `build.gradle.kts` owns native preparation and public build aliases.
- `.node-version` declares the local Node version; the Release workflow currently declares a different Node version.
- `.gitmodules` lists native and UI submodules. Initialize recursively for native/package builds.
- `cmake/` builds native dependencies for supported host/target combinations.
- `.github/workflows/release-desktop.yml` builds and starts Linux/Windows installers before uploading them to a draft Release.

Linux startup loads system WebKit first. When that succeeds, `DesktopAppModule` tells
`VipsSharedLibraries` to reuse the loaded system GLib family. Loading bundled GObject or GIO
against a different system GLib can fail with missing symbols. Without an available WebView,
the decoder retains its bundled-library path. Windows keeps its bundled dependencies.
The Linux build also prioritizes the builder's system GLib pkg-config files. Release builds
therefore compile against Bookworm's GLib 2.74 headers instead of selecting newer APIs from the
bundled GLib headers. `cmake/check-linux-glib-abi.sh` rejects native consumers with GLib symbols
missing from that baseline. The bundled GLib source and revision remain unchanged.

The desktop smoke scripts require the `Application dependencies initialized` log event as well
as a visible window. Inspect the captured page too: startup error windows are still visible
windows. The Linux CI fixture prepares a desktop-menu directory and a private D-Bus session
with a test keyring; it does not change application credential storage.
`scripts/verify-linux-installer.sh` owns fixture preparation, installation, and native probes.
The desktop workflow can repeat installation checks on an existing artifact after verifying its
hash and confirming that application sources and the selected platform's build steps did not change.

## Detailed catalog lists

`komelia-ui/.../common/cards/DetailedListCardLayout.kt` owns the shared book/series list geometry. The text column determines row height above a minimum cover height; the cover fills the measured row without imposing an image intrinsic size. Book rows use a compact cover, page count and reading progress; series rows retain their metadata layout. Search results and series book lists reuse this layout. Read lists select the numbered single-column variant of `BookLazyCardGrid`; pagination, selection, drag reordering and reading callbacks share the existing owners. `DetailedListCardLayoutTest` runs Compose measurement regressions on JVM through `:komelia-ui:jvmTest`.

### Android reader and search validation

- `komelia-domain/core/src/androidMain/kotlin/snd/komelia/AndroidWindowState.kt` controls reader system bars; preserve system navigation by default and hide it only for explicitly selected EPUB immersion.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderBackAction.kt` defines mobile PDF's controls-then-exit Back behavior.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/EpubContent.kt` owns the EPUB host safe drawing insets, including landscape cutouts.
- Local and global search share `SearchTextField`; long placeholders remain one line. Runtime checks are in [Android device validation](../harness/android-device-validation.md).

- `EpubDisplaySettings` stores native EPUB immersion and extra top spacing separately from web reader preferences; database migration V15 preserves existing reader settings. Current defaults use automatic safe-area handling with zero additional spacing; explicit custom spacing is retained.
- `EpubBackground.kt` maps Komga/TTU reading themes to the native margin background; Android WebView initialization happens once per created view so theme recomposition does not reload the book.
- Standalone Android update discovery is enabled independently of self-installation. `AppUpdatesViewModel` exposes manual checks and retry states; `StartupUpdateChecker` preserves the opt-out and daily throttle. Debug update actions open the download page.

Android Lanczos3 and Mitchell upsampling use the cancellable, premultiplied-alpha `PixelUpsampler` in `komelia-infra/image-decoder/shared`. `AndroidReaderImage` applies it to frames and padded source tiles on the background image pipeline; output tile sizes stay bounded while zooming. Desktop/Wasm sampling choices remain platform-specific.

Local-library index removal deletes reading progress before books and series inside a database transaction. Refresh, exclusion, and library removal share that cleanup; source files are never deleted by index cleanup. SQLite integration tests cover external file/folder deletion, preserved progress, and rollback.

## Optional surface material

`AppSurfaceStyle` is independent of `AppTheme`. SQLite migration V19 adds `surface_style` and `reduce_transparency`; browser `AppSettings` supplies legacy defaults. `AppearancePreference` serializes/coalesces accepted choices and exposes save failure/retry without discarding a newer selection.

`GlassSurface.kt` owns all experimental Haze 2.0.1 APIs. Each backdrop captures content separately from controls; Haze's default `Behind` selection excludes ancestor sources. `KomeliaPageScaffold` measures optional toolbars, preserving content scroll range while allowing content beneath floating chrome. Android capability checks and reduced transparency select solid surfaces. `GlassSamplingBudget` pauses sampling during active scrolling and resumes after 250ms of idle time; its owners are released when their composition leaves. All main destinations share one material and navigation geometry, including mobile search. While capture is paused, Haze retains the last available frame in the current backdrop scope; it resumes after scrolling or editing settles. This avoids changing the chrome to an opaque panel during each gesture. The retained frame is released with the scope.

The two EPUB bundles import `komelia-epub-reader/shared/chrome-appearance.mjs` and its scoped CSS. Both Gradle npm build tasks declare the shared directory as an input, so chrome-only changes invalidate the embedded HTML bundles. `getChromeAppearance` is an optional host bridge, and only elements marked `data-komelia-chrome` receive material styles. `RetainedEpubReader` owns the selected reader implementation so changing the EPUB preference applies to the next entry into the same book.

`ImageReaderToolbar` owns the compact image/PDF header. It uses the existing standard icon button with 20dp glyphs and platform touch targets. EPUB's `EpubReaderToolbar.vue` and TTU `book-reader-header.svelte` use the shared CSS for a full-width top bar, two-line titles and matching action sizes. Secondary actions stay in accessible overflow menus; their reading, bookmark and settings callbacks remain in the parent readers. Native layout continues to own WebView safe insets.

The [immersive glass references](../design/immersive-glass-20261025/README.md) guide the shared presentation layer. `HomeViewModel` loads up to 20 recently read books independently of configured Home groups, including completed and unfinished books. `RecentlyReadBooks.kt` merges the selected catalog and its local-folder libraries by real read date and book identity. The overview replaces the exact default reading queries in All; configured groups remain selectable and stored unchanged. Re-entering Home reloads saved progress even without an SSE event. `HomeFeaturedBook` chooses the latest started, unfinished book within the visible scope, including books whose page count is unknown. `ContinueReadingHero` captures only that book's thumbnail; browsing and settings backgrounds remain neutral. `BookReadingProgress` supplies a clamped fraction for covers, chapter rows and reading controls. An unfinished book with an unknown page count returns no fraction, so the UI uses a status label instead of inventing a percentage. EPUB details use the book title. A failed cover load collapses the detail image to a compact placeholder and the home hero to its heading and reading controls, including books with a known page count. `KomeliaPageScaffold` keeps the detail reading bar outside the captured scrolling content so it can sample the content beneath it. The navigation display order is separate from persisted `AppDestination` ordinals. The hero stacks its title and reading action on narrow layouts or with large system text; `ContinueReadingHeroLayoutTest` measures the actual controls.

`SeriesViewModel.initialize` and `ReadListViewModel.initialize` refresh retained book lists on re-entry so progress does not depend on receiving an SSE event while the reader is visible. `CatalogProgressRefreshTest` verifies refresh without duplicate event subscriptions.

`SettingsScreenLayout` keeps the active form and navigation composition movable across its responsive breakpoint; focus and popup owners are retained instead of disposed during a resize. `SettingsScreenLayoutTest` checks lifecycle continuity and updated content.

`SettingsSection` provides solid form groups and `SettingsChoiceRow` switches between horizontal and stacked labels according to available width and font scale. `GlassSurfaceDecoration` draws neutral material edges; it does not add scene artwork or simulated colored reflections. Reduced transparency and unsupported renderers retain the solid material path with the same glass outline. Glass tint and backdrop grading preserve readable text while transmitting content variation; control surfaces use a 12dp blur radius. The updated EPUB toolbar CSS is scoped to chrome attributes and leaves publication styling and progress handling unchanged. TTU toolbar groups wrap on narrow viewports so their 48px touch targets, including Close, remain inside the floating bar.

Mobile poster density defaults to 3 columns below 840dp, 4 below 1200dp and 6 above it, while still scaling with the persisted density setting. `CoverCardCaption` reserves height for the system font scale. Desktop poster grids retain adaptive widths. `BottomBulkActionsPanel` is an inline footer shared by series, collections and read lists. Their content uses the remaining column height; the footer reserves the navigation inset.

Shared chrome uses `KomeliaChromeStyle` for 28dp blur, neutral tint and borderless highlight shadows. `KomeliaChromeContent` scopes readable foreground colors without rebuilding its content. Native navigation, menus and reader controls share these rules with EPUB chrome CSS. `BrowsePageHeader` unifies destination and mobile settings headers; main destinations use `KomeliaPageScaffold` so header effects are siblings of the content capture. `ChromeFilterChip` shares their white selection light with native semantics. Glass toolbars have no scroll elevation. `LocalPageTopInset` and `KomeliaSafeTopBar` let the background reach the status area while controls consume the cutout inset and header material once. EPUB keeps native safe-area ownership and defaults additional top spacing to zero. See the [shared glass verification](../verification/shared-glass-20261027.md).

## Web rendering compatibility

Wasm configurations use the `compose-web` core-library version to include the semantics-owner fix
for CMP-9368/CMP-10623. Android and JVM retain `compose-multiplatform`; Material3 retains its own
version. The override lives in the root Gradle build and does not change submodule revisions.

The Web Skiko bindings and JavaScript/Wasm runtime must use the same `skiko-web` version. Skiko
0.153 publishes the runtime as the `skiko-runtime` variant of `skiko-wasm-js`; the older Compose
Gradle plugin otherwise requests the obsolete standalone runtime module. Keep that variant
selection paired with the Wasm dependency override. Remove the overrides when the shared stable
Compose stack contains the accessibility fix. Browser popup and bitmap-decoder tests guard this
boundary; a successful Wasm compile alone does not verify the runtime pairing.

`BasicAppDialog`, the dropdown and exposed-menu wrappers, `KomeliaModalBottomSheet` and the tooltip wrappers apply the same glass material in native overlay windows. They share the owning content's Haze sources and retain native focus, selection and dismissal behavior. Reduced transparency uses opaque material. Main navigation retains its white selection light and adds a rounded position marker plus a bold label. Material icons use the Rounded family; the reader keeps lightweight outline controls. Filled input fields use the shared rounded shape on all four corners.

`BrowseOptionsButton` keeps library scope, content type, sorting, page size and filters behind the title row. Library list components suppress their duplicate toolbar when that header owns the controls. `BrowseSearchHeader` replaces the title with an inline search field without adding a second row; closing it retains and displays the query. Its input and search button request focus after placement. The existing screen models still own all query, filter and paging state.

`SingleLayerPageTransition` presents the requested destination or detail screen immediately. It animates only the incoming layer, keeps initial content visible and respects reduced motion. Navigation must not wait for the outgoing page to fade before composing the requested screen. Backdrop capture scopes and the Balanced glass-quality profile remain unchanged.

Komga EPUB's `TocListItem` owns separate chapter and expansion buttons. Expansion uses a transparent 48px control with a 20px chevron, an accessible label and expanded state. Chapter titles wrap within the available width. `TocList` owns the opened groups; the reader still owns navigation and progress.
