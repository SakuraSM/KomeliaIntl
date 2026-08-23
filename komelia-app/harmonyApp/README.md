# Komelia for HarmonyOS

This directory is a fully native ArkTS/ArkUI implementation. It does not load the Compose Wasm application in ArkWeb and does not depend on Android compatibility.

## Platform baseline

- DevEco Studio 6.1.1 Release or newer
- HarmonyOS SDK API 20, compatible API 16
- HarmonyOS 6 phone, tablet, or 2-in-1 device
- Bundle ID: `io.github.zhengningning.komelia.harmony`

The application uses Network Kit for Komga requests, Asset Store for encrypted credentials, Preferences for non-secret settings, Image Kit for authenticated covers/pages, Core File Kit for local publication files, and Reader Kit for EPUB layout.

## Build

Open this directory in DevEco Studio once to download SDK components and create a local signing profile. Then run from the repository root:

```shell
./gradlew harmonyDebug
./gradlew harmonyRelease
```

The equivalent direct command is:

```shell
devecocli build --product default --modules entry@default --build-mode debug
```

## Real-device validation

1. Enable Developer mode and USB debugging on the HarmonyOS device.
2. Connect the device and approve the host authorization prompt.
3. Verify discovery with `devecocli device list` or the SDK `hdc list targets`.
4. Run `./gradlew harmonyDeviceRun`.
5. Validate HTTPS sign-in, library/search/detail flows, image/PDF reading, EPUB chapter navigation, progress restoration, system back, safe areas, rotation, dark mode, and Chinese/English resources.

Plain HTTP is rejected unless the user explicitly confirms the LAN risk. Invalid TLS certificates are never bypassed. Credentials are not written to Preferences, logs, or repository files.

## Current preview boundary

The native online reading path is implemented. Offline queue orchestration, complete Readium locator conversion, RAR extraction, background download constraints, ONNX enhancement, release signing, and AppGallery checks remain follow-up work and must not be advertised as complete before device QA.
