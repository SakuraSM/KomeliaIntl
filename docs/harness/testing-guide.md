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
npm run build
```

`komga-webui` has no declared `check` script; its `build` script performs `vue-tsc --noEmit` before Vite.

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
