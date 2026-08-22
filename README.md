# Komelia Intl - Komga media client

[English](README.md) | [简体中文](README_zh-CN.md)

This repository modifies [Snd-R/Komelia](https://github.com/Snd-R/Komelia). It keeps the upstream Komga client and adds Simplified Chinese localization, adaptive navigation, a mobile UI overhaul, reader interaction fixes, and builds for each supported platform.

## Changes in this fork

- Replaces the global drawer with bottom navigation on phones and a Navigation Rail on wider windows. Each destination keeps its navigation and scroll state.
- Uses one layout, spacing, color, shape, and motion system across Light, Dark, and OLED themes. Reduced motion, keyboard focus, and back navigation are supported.
- Redesigns Home groups, Library scope selection, filters, search, Settings, series details, and book details. Home shows the groups that fit and moves the rest into More.
- Keeps cover cards aligned with fixed title slots for one-line and two-line titles.
- Refines image and EPUB reader controls. Reader chrome hides automatically, and progress dragging does not trigger page turns.
- Adds Simplified Chinese to the Compose app, Android system screens, and the EPUB reader. Users can select System, English, or Simplified Chinese in the app.
- Keeps offline PDF, RAR, and EPUB support, page retry, LAN address switching, and existing database migrations.
- Publishes a universal Android APK, Windows MSI, Linux DEB, macOS ARM64 DMG/JAR, and Wasm WebUI.

## Downloads

- Fork releases: <https://github.com/SakuraSM/KomeliaIntl/releases/latest>
- Upstream releases: <https://github.com/Snd-R/Komelia/releases>
- Upstream Google Play: <https://play.google.com/store/apps/details?id=io.github.snd_r.komelia>
- Upstream F-Droid: <https://f-droid.org/packages/io.github.snd_r.komelia/>
- Upstream AUR: <https://aur.archlinux.org/packages/komelia>

## App screenshots

<img src="/screenshots/app-overview-v0.18.13.png" alt="Komelia Android Home, Library filters, book details, and Settings" width="100%">

> These screenshots come from the Android app. The resource filename and source domains in the book details screen are redacted. The montage contains no account, server address, or test credentials.

## Translations
You can help translate this project to your language by using service provided by [Weblate](https://hosted.weblate.org/engage/komelia/)

[![Translation status](https://hosted.weblate.org/widget/komelia/horizontal-auto.svg)](https://hosted.weblate.org/engage/komelia/)

## Build instructions
Make sure you download all git submodules\
`git clone --recurse-submodules https://github.com/Snd-R/Komelia` \
if you already cloned repository without recurse command run\
`git submodule update --init --recursive`

Requires jdk 17 or higher\
Android and JVM targets require C and C++ compiler for native libraries and Node.js for epub readers build.\
Recommended way to build is by using docker images that contain all required build dependencies.\
If you want to build with system toolchain and dependencies try running:\
`./gradlew komeliaBuildNonJvmDependencies` (Linux Only)

## Desktop App
Replace <*platform*> placeholder with your target platform. \
Available platforms include: `linux-x86_64`, `windows-x86_64`

- `docker build -t komelia-build-<platfrom> . -f ./cmake/<paltform>.Dockerfile `
- `docker run -v .:/build komelia-build-<paltform>`
- `./gradlew <platform>_copyJniLibs`
- `./gradlew buildEpubReaders`

Then choose your packaging option:
- `./gradlew :desktopRun` to launch desktop app
- `./gradlew :desktopJar` output in `./komelia-app/desktopApp/build/compose/jars`
- `./gradlew :desktopDeb` output in `./komelia-app/desktopApp/build/compose/binaries`
- `./gradlew :desktopMsi` output in `./komelia-app/desktopApp/build/compose/binaries`
- `./gradlew :desktopDmg` output in `./komelia-app/desktopApp/build/compose/binaries`

## Android App
Replace <*arch*> placeholder with your target architecture.\
Available architectures include:  `aarch64`, `armv7a`, `x86_64`, `x86`

- `docker build -t komelia-build-android . -f ./cmake/android.Dockerfile `
- `docker run -v .:/build komelia-build-android <arch>`
- `./gradlew <arch>_copyJniLibs`
- `./gradlew buildEpubReaders`

Then choose app build option:

- `./gradlew :androidDebug` output in `./komelia-app/androidApp/build/outputs/apk/debug`
- `./gradlew :androidRelease` output in `./komelia-app/androidApp/build/outputs/apk/release`


## Komf Wasm WebUI
run `./gradlew :komfWebUI` output will be in `./build/komf-webui`

## Komf Wasm Extension
for chrome `./gradlew :komfExtensionChrome` \
for firefox `./gradlew :komfExtensionFirefox` \
output archive will be in `./komelia-komf-extension/app/build/distributions`
