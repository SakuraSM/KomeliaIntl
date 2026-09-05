# Development map

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

## Build and dependency surfaces

- `settings.gradle.kts` is the module registry and repository policy.
- `gradle/libs.versions.toml` is the dependency catalog.
- Root `build.gradle.kts` owns native preparation and public build aliases.
- `.node-version` declares the local Node version; the Release workflow currently declares a different Node version.
- `.gitmodules` lists native and UI submodules. Initialize recursively for native/package builds.
- `cmake/` builds native dependencies for supported host/target combinations.
- `.github/workflows/release-desktop.yml` validates and uploads Linux/Windows desktop packages to an existing draft Release.

## Detailed catalog lists

`komelia-ui/.../common/cards/DetailedListCardLayout.kt` owns the shared book/series list geometry. The text column determines row height above a minimum portrait-cover height; the cover fills the measured row without imposing an image intrinsic size. Search results and series book lists reuse this layout. `DetailedListCardLayoutTest` runs Compose measurement regressions on JVM through `:komelia-ui:jvmTest`.

### Android reader and search validation

- `komelia-domain/core/src/androidMain/kotlin/snd/komelia/AndroidWindowState.kt` controls reader system bars; preserve system navigation by default and hide it only for explicitly selected EPUB immersion.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/image/common/ReaderBackAction.kt` defines mobile PDF's controls-then-exit Back behavior.
- `komelia-ui/src/commonMain/kotlin/snd/komelia/ui/reader/epub/EpubContent.kt` owns the EPUB host safe drawing insets, including landscape cutouts.
- Local and global search share `SearchTextField`; long placeholders remain one line. Runtime checks are in [Android device validation](../harness/android-device-validation.md).

- `EpubDisplaySettings` stores native EPUB immersion and extra top spacing separately from web reader preferences; database migration V15 preserves existing reader settings and defaults to automatic safe-area handling plus 8dp spacing.
- `EpubBackground.kt` maps Komga/TTU reading themes to the native margin background; Android WebView initialization happens once per created view so theme recomposition does not reload the book.
- Standalone Android update discovery is enabled independently of self-installation. `AppUpdatesViewModel` exposes manual checks and retry states; `StartupUpdateChecker` preserves the opt-out and daily throttle. Debug update actions open the download page.

Android Lanczos3 and Mitchell upsampling use the cancellable, premultiplied-alpha `PixelUpsampler` in `komelia-infra/image-decoder/shared`. `AndroidReaderImage` applies it to frames and padded source tiles on the background image pipeline; output tile sizes stay bounded while zooming. Desktop/Wasm sampling choices remain platform-specific.

Local-library index removal deletes reading progress before books and series inside a database transaction. Refresh, exclusion, and library removal share that cleanup; source files are never deleted by index cleanup. SQLite integration tests cover external file/folder deletion, preserved progress, and rollback.
