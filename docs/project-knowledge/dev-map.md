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
| `komelia-domain/offline` | download/cache domain behavior and offline contracts | platform UI |
| `komelia-infra/database/*` | transactions and SQLite/Wasm persistence implementations | user interaction policy |
| `komelia-infra/image-decoder/*` | shared decoder contract and VIPS/Wasm implementations | reader navigation state |
| `komelia-infra/webview` | platform WebView bridge used by EPUB and web content | publication business rules |
| `komelia-infra/onnxruntime/*` | image enhancement API and JVM implementation | UI preference ownership |
| `komelia-infra/jni` | native resource loading and bindings | domain workflows |
| `third_party/*` | vendored/submodule dependencies | fork-specific application fixes |

## Dependency direction

Applications assemble platform implementations and depend on shared app/UI/domain modules. UI consumes domain contracts and selected infrastructure abstractions. Domain code may expose infrastructure contracts used for transactions, decoding, and inference, but must remain free of Compose presentation state. Platform implementations stay in Android, JVM, or Wasm source sets.

Some Gradle project dependencies are not a textbook layered graph because shared interfaces and implementations are split across modules. Before moving a type, inspect every `build.gradle.kts` consumer and source-set actual implementation rather than relying only on directory names.

## Build and dependency surfaces

- `settings.gradle.kts` is the module registry and repository policy.
- `gradle/libs.versions.toml` is the dependency catalog.
- Root `build.gradle.kts` owns native preparation and public build aliases.
- `.node-version` declares the local Node version; the Release workflow currently declares a different Node version.
- `.gitmodules` lists native and UI submodules. Initialize recursively for native/package builds.
- `cmake/` builds native dependencies for supported host/target combinations.
- `.github/workflows/release-desktop.yml` validates and uploads Linux/Windows desktop packages to an existing draft Release.
