# Komelia Intl

[English](README.md) | [简体中文](README_zh-CN.md)

[![Latest release](https://img.shields.io/github/v/release/SakuraSM/KomeliaIntl?display_name=tag&sort=semver)](https://github.com/SakuraSM/KomeliaIntl/releases/latest)
[![License](https://img.shields.io/github/license/SakuraSM/KomeliaIntl)](LICENSE)

Komelia Intl is the `SakuraSM/KomeliaIntl` fork of [Komelia](https://github.com/Snd-R/Komelia), a cross-platform client for a [Komga](https://komga.org/) media server. It adds Simplified Chinese localization, an adaptive interface, reader fixes, and fork-specific release support.

Komga-backed features need a reachable Komga server and account. Android and desktop can also start in local-library mode without a server and index supported books from a folder on the device.

## Core changes in this fork

### UI redesign

The fork reworks navigation, Home groups, Library filters, search, Settings, and detail pages for phones and wide screens. Light, Dark, and OLED themes share the same spacing, color, shape, and motion rules. The UI also supports reduced motion and keyboard focus.

### Automatic remote and LAN switching

You can configure a primary remote URL and an optional LAN URL. When automatic switching is enabled, Komelia probes the LAN address and uses it when reachable. Otherwise, it keeps the primary remote address. Android checks again when network connectivity changes.

### Local downloads and offline reading

On supported native targets, you can download books, browse the local cache by series, book, or media type, and remove cached items. Downloaded CBZ, CBR, PDF, and EPUB files remain available in Offline mode.

### Local folder libraries

Android and desktop can add a local folder without signing in to Komga. Komelia indexes supported files recursively, groups books by their parent folder, keeps the folder permission across restarts, and detects additions and removals on startup or during the scheduled scan. Android supports CBZ/ZIP, CBR/RAR, EPUB, and PDF; desktop currently supports CBZ/ZIP, CBR/RAR, and EPUB. Source files stay in place and are never deleted by removing a local library from Komelia. Browser/Wasm builds do not provide persistent folder libraries.

## Other changes

- Adds in-app language selection for System, English, and Simplified Chinese. The Compose app, Android system pages, and EPUB controls use the selected language.
- Refines image and EPUB reader controls, page retry, progress dragging, and Back behavior.
- Uses this repository for app updates and shows announcements from both this fork and upstream Komelia.
- Includes an in-progress native HarmonyOS 6+ client written with ArkTS and ArkUI. It uses HarmonyOS platform services instead of an APK compatibility layer or an ArkWeb wrapper.

## What you can do

- Browse libraries, collections, and read lists. Search and filter series and books.
- Read CBZ, CBR, PDF, and EPUB files with the built-in image and EPUB readers.
- Edit series and book metadata. Connect to Komf for supported metadata workflows.

## Maintenance

I maintain this repository independently. I track upstream Komelia and aim to ship an iteration about once a week. The schedule may shift based on upstream changes, test results, and available time. Thanks for using Komelia Intl.

## Supported targets

The source tree contains these build targets. A GitHub Release can contain only the platforms built for that version, so use its **Assets** list as the source of truth.

| Target | Gradle task | Typical package |
|---|---|---|
| Android | `androidRelease` | APK |
| Windows x86_64 | `desktopMsi` | MSI |
| Linux x86_64 | `desktopDeb` | DEB |
| macOS | `desktopDmg` | DMG |
| Desktop on the current OS | `desktopJar` | JAR |
| Browser, WebAssembly | `komfWebUI` | Static web files |
| Komf extension for Chrome | `komfExtensionChrome` | ZIP |
| Komf extension for Firefox | `komfExtensionFirefox` | ZIP |
| HarmonyOS 6+ preview | `harmonyDebug` | HAP |

### HarmonyOS native preview

The HarmonyOS client is a separate DevEco project under `komelia-app/harmonyApp`. It targets HarmonyOS 6 / API 20 while retaining API 16 compatibility. Install DevEco Studio 6.1.1 or newer with the matching HarmonyOS SDK and configure a signing profile first.

- `./gradlew harmonyDebug` builds the debug HAP.
- `./gradlew harmonyRelease` builds the signed release HAP when signing is configured.
- `./gradlew harmonyDeviceRun` builds, installs, and launches the app on a connected HarmonyOS device.

The native client currently covers Komga sign-in, adaptive navigation, catalog and detail flows, image/PDF reading, EPUB integration, offline state, and management surfaces. Signed physical-device validation and AppGallery release checks remain in progress. See [`komelia-app/harmonyApp/README.md`](komelia-app/harmonyApp/README.md) for the verified scope and current limitations.

## Download the app

- [Latest Komelia Intl Release](https://github.com/SakuraSM/KomeliaIntl/releases/latest)
- [All Komelia Intl Releases](https://github.com/SakuraSM/KomeliaIntl/releases)

The standalone Android build uses the package ID `io.github.zhengningning.komelia` and this repository's signing certificate. Android cannot update an installation signed by another distributor. If Android reports a signature conflict, uninstall the other build first or keep using the same distribution channel.

The [upstream Releases](https://github.com/Snd-R/Komelia/releases), [Google Play package](https://play.google.com/store/apps/details?id=io.github.snd_r.komelia), [F-Droid package](https://f-droid.org/packages/io.github.snd_r.komelia/), and [AUR package](https://aur.archlinux.org/packages/komelia) contain upstream Komelia, not this fork's changes.

## Android UI preview

<img src="screenshots/app-overview-v0.18.13.png" alt="Komelia Android Home, Library filters, book details, and Settings" width="100%">

The montage uses test content. The resource filename and source domain are redacted. It contains no account, server address, or test credentials.

## Build from source

Release workflows use JDK 21 and Node.js 24. The local `.node-version` currently declares Node.js 20; the [Harness scorecard](docs/harness/scorecard.md) records this toolchain split until the repository adopts one declaration. Android and desktop packages also need the platform's native image and WebView libraries. The files under `cmake/` build those dependencies with Docker for supported targets.

Clone this fork with its submodules:

```shell
git clone --recurse-submodules https://github.com/SakuraSM/KomeliaIntl.git
cd KomeliaIntl
```

If you already cloned the repository, initialize the submodules before building:

```shell
git submodule update --init --recursive
```

Build the EPUB reader resources before packaging an app:

```shell
./gradlew buildEpubReaders
```

Use the root build aliases for common targets:

```shell
./gradlew androidDebug
./gradlew desktopJar
./gradlew komfWebUI
```

The outputs are under the corresponding module's `build/` directory. For release packaging and native dependency commands, follow [the desktop Release workflow](.github/workflows/release-desktop.yml) and [the engineering harness](docs/harness/README.md).

## Contribute

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Translation changes also use the [Simplified Chinese contribution guide](docs/i18n/CONTRIBUTING_zh-CN.md) and [glossary](docs/i18n/glossary_zh-CN.md).

Report bugs and request changes in [GitHub Issues](https://github.com/SakuraSM/KomeliaIntl/issues). Do not include credentials, private server addresses, or unredacted library content.

## License and upstream

Komelia Intl is available under the [Apache License 2.0](LICENSE). The project is based on [Snd-R/Komelia](https://github.com/Snd-R/Komelia); upstream and fork changes keep their respective copyright notices.
