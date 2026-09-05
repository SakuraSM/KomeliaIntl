# Komelia for HarmonyOS

This directory is a fully native ArkTS/ArkUI implementation. It does not load the Compose Wasm application in ArkWeb and does not depend on Android compatibility.

## Platform baseline

- DevEco Studio 6.1.1 Release or newer
- HarmonyOS SDK API 20, compatible API 16
- HarmonyOS 6 phone, tablet, or 2-in-1 device
- Bundle ID: `io.github.sakurasm.komeliaintl`

The application uses Network Kit for Komga requests, Asset Store for encrypted credentials, Preferences for non-secret settings, Image Kit for authenticated covers/pages, Core File Kit for local publication files, and Reader Kit for EPUB layout.

## Local content and current main features

The native port now includes local-only startup, folder/file libraries, local and downloaded content filters, merged Home groups and unified search, independent local progress, persistent download byte progress, and EPUB immersion/top spacing. The Local tab opens imported content; Settings → Local libraries manages scan preferences and excluded paths.

Folder selection is capability-gated. Without durable folder access, select files to copy them into the app's private storage. Removing a library never deletes a granted source; its app-owned import copy is removed after confirmation. Imports are snapshots on these devices; to replace an existing snapshot, remove that library and import the file again. Automatic hourly scans run while foregrounded and when returning to the app, not after process termination.

Current source support is CBZ/ZIP, PDF, EPUB, JPG/JPEG/PNG/WebP/GIF/BMP. ZIP64, encrypted/split archives, legacy non-ASCII ZIP filename encodings, RAR/CBR and 7z are unsupported. Archive entries are capped at 32 MiB, central directories at 8 MiB and 20,000 entries. Invalid source inspection is reported without erasing an existing index.

See [feature port status](feature-port-status.md) for the code-level verification and the device scenarios still pending. The Android Kotlin image-sampling implementation and Compose gesture fixes are not consumed by this native renderer.

## Build

Open this directory in DevEco Studio once to download SDK components and create a local signing profile. Then run from the repository root:

```shell
./gradlew harmonyDebug
./gradlew harmonyRelease
./gradlew harmonyTest
./gradlew harmonyDeviceTest
```

The equivalent direct command is:

```shell
devecocli build --product default --modules entry@default --build-mode debug
```

`harmonyTest` runs the local Hypium suite and also checks the generated result summary. This extra gate is required because some Hvigor versions can return a successful process status even when individual test cases fail.

## Real-device validation

For the Home short-content alignment regression, capture layout dumps at the
same viewport and scroll position, first in Overview and then in Continue
Reading, with at least one continue-reading book available. Run:

```shell
node scripts/check-home-group-alignment.mjs /tmp/overview.json /tmp/continue-reading.json
```

The gate fails if the authenticated page or required card is missing, the
viewport changes, or the first card moves vertically by more than two physical
pixels. This checks layout, not frame-by-frame cover continuity. Keep captures
outside the repository; they may contain private library data. For acceptance
on a physical device, also record repeated group switches and verify rotation
with the system rotation lock both enabled and disabled. Forced display
rotation in UI tests is not proof that the system rotation lock is respected.

1. Enable Developer mode and USB debugging on the HarmonyOS device.
2. Connect the device and approve the host authorization prompt.
3. Verify discovery with `devecocli device list` or the SDK `hdc list targets`.
4. Run `./gradlew harmonyDeviceRun`.
5. Run `./gradlew harmonyDeviceTest` to install and execute the Hypium device suite.
6. Validate HTTPS sign-in, library/search/detail flows, image/PDF reading, EPUB chapter navigation, progress restoration, system back, safe areas, rotation, dark mode, and Chinese/English resources.

For the release-candidate gate, first configure a local DevEco signing profile
and build the signed HAP. Then run the strict real-device harness with the
physical HDC target and signed artifact:

```shell
HDC_TARGET=<physical-device-target> \
HAP_PATH=/absolute/path/to/entry-default-signed.hap \
./gradlew harmonyRealDeviceValidation
```

The harness rejects localhost and emulator products, requires API 20+, verifies
the HAP signature with the SDK signing tool, runs the device suite, reinstalls
the candidate HAP, and writes a manual Reader Kit/Speech Kit/offline/AI/stability
checklist under `/private/tmp`. The report intentionally excludes credentials,
server URLs, and private publication titles. A generated checklist is not a
pass: every manual gate must be completed on the recorded build and device.

Plain HTTP is rejected unless the user explicitly confirms the LAN risk. Invalid TLS certificates are never bypassed. Credentials are not written to Preferences, logs, or repository files.

## Android visual-parity harness

Compare content-independent regions only after both apps are set to the same
logical viewport width, language, theme, route, scroll position, data state,
and permission state. The harness reports raw cross-platform pixel similarity
separately from repeated layout rhythm, so different system-font rasterizers do
not hide a spacing regression or falsely fail an otherwise matching layout.

```shell
python3 scripts/visual_parity.py \
  --android /tmp/android-settings.png \
  --harmony /tmp/harmony-settings.png \
  --android-logical-width 377.29 \
  --harmony-logical-width 377.14 \
  --android-region 34,366,1012,977 \
  --harmony-region 34,340,1012,973 \
  --expected-lines 8 \
  --min-layout-score 98
```

Region coordinates are measured after each full screenshot is normalized to
the Android screenshot width. Use a quiet sample column crossing card outlines
and dividers, not text or icons. Screenshots must remain outside the repository
when they contain account or library data. A passing region is evidence only
for that page/state; it is not an application-wide 98% claim.

For the Compact home toolbar, export an Android UIAutomator XML and a HarmonyOS
`uitest dumpLayout` JSON while both apps show the same visible group labels,
then gate button widths, gaps, total span, and the content start offset:

```shell
python3 scripts/home_toolbar_parity.py \
  --android-layout /tmp/android-home.xml \
  --harmony-layout /tmp/harmony-home.json \
  --android-logical-width 377.293 \
  --harmony-logical-width 377.143 \
  --min-score 98
```

For the Medium home shell, capture both apps in landscape or another viewport
between 600 and 839 logical pixels. The two accounts may expose different
enabled home groups, so pass only labels visible on both captures. The gate
compares the rail, shared chips, section rhythm, grid spacing, column count,
and card-width fraction without reading private cover pixels:

```shell
python3 scripts/home_medium_parity.py \
  --android-layout /tmp/android-home-medium.xml \
  --harmony-layout /tmp/harmony-home-medium.json \
  --android-logical-width 815.287 \
  --harmony-logical-width 816 \
  --labels '概览,继续阅读,最近添加的书籍,最近添加的系列,最近更新的系列' \
  --min-score 98
```

Safe-area width differences are normalized separately from the 80dp/vp rail.
An overflow-only group appearing under “More” on one platform is a data or
local-settings difference, not a layout failure; verify the picker contents
before changing the overflow algorithm.

The Medium settings gate likewise compares each destination relative to its
own usable content bounds, because landscape cutout insets differ between
devices. It covers the rail, page/title rhythm, shared application-settings
rows, row stride, label alignment, and normalized card width:

```shell
python3 scripts/settings_medium_parity.py \
  --android-layout /tmp/android-settings-medium.xml \
  --harmony-layout /tmp/harmony-settings-medium.json \
  --android-logical-width 815.287 \
  --harmony-logical-width 816 \
  --min-score 98
```

Keep both captures on the settings root. The gate intentionally rejects a
Harmony home capture or an Android settings-detail stack instead of treating
different navigation states as visual differences.

The Compact, Medium, and Expanded search pages have a stricter structure gate because
they contain an input, a segmented selector, and repeated result cards.
Capture both layout trees at the same logical width and with the same result
title visible:

```shell
python3 scripts/search_layout_parity.py \
  --android-layout /tmp/android-search.xml \
  --harmony-layout /tmp/harmony-search.json \
  --android-logical-width 377.293 \
  --harmony-logical-width 377.143 \
  --min-score 98
```

This gate compares the input and segment geometry, list start, first card and
cover bounds, plus the matching title anchor. When a wider capture contains a
navigation rail, the gate also compares the rail and navigation-item rhythm.
Each platform's destination content is used as the origin so device-specific
landscape safe areas do not create a false mismatch. Library data in layout
dumps is private test evidence and must stay outside the repository. Small
fixed layout tokens such as gaps allow at most one logical pixel of rendering
discretization; responsive container sizes continue to be compared as
destination-relative fractions.

The Compact, Medium, and Expanded library pages use their own structure gate. Capture the
default series view with the same library scope, page size, sort order, and
first two series visible on both platforms:

```shell
python3 scripts/library_layout_parity.py \
  --android-layout /tmp/android-library.xml \
  --harmony-layout /tmp/harmony-library.json \
  --android-logical-width 377.293 \
  --harmony-logical-width 377.143 \
  --min-score 98
```

Medium and Expanded captures also compare the navigation rail. Trailing controls are
measured from each destination's right edge so asymmetric landscape safe areas
do not create false differences. If a landscape viewport clips the caption
slot of the first card row, the gate compares card top, width and column gap
but deliberately omits the clipped height. When a complete responsive card is
visible, its height-to-width ratio is compared instead of its absolute height;
this keeps the fixed `0.703` cover ratio and caption slot under test without
mistaking different platform safe-area widths for card drift.

This gate covers the library scope selector, series count, filter and page-size
controls, plus the first grid row. Its default text anchors are only suitable
for the private QA account; override `--series-count-label`, `--first-title`,
and `--second-title` for another fixture. Never commit content-bearing dumps.

The Compact and Medium series-detail pages have a same-state structure gate for
the toolbar, responsive cover, hero chips, summary and visible metadata rows:

```shell
python3 scripts/series_detail_layout_parity.py \
  --android-layout /tmp/android-series-detail.xml \
  --harmony-layout /tmp/harmony-series-detail.json \
  --android-logical-width 377.293 \
  --harmony-logical-width 377.143 \
  --min-score 98
```

Both captures must show the same series, locale, theme, account permission state
and logical viewport width. Android and ArkUI accessibility trees expose Text
and clickable-chip bounds differently, so the gate compares shared anchors and
only compares dimensions when both trees expose the same semantic container.
Medium captures additionally compare the navigation rail and normalize positions
against each platform's destination content, avoiding false mismatches from an
Android-only landscape safe area. Viewport-clipped summary height and wrapped
metadata values are content- and screen-height-dependent, so they are not used
as Medium vertical anchors. Override the text arguments for a public fixture and
keep private layout dumps outside the repository.

The Medium book-detail page has a dedicated same-book geometry gate for the
toolbar, 0.703 cover, series link, volume summary, split read control, file
metadata columns and navigation rail:

```shell
python3 scripts/book_detail_layout_parity.py \
  --android-layout /tmp/android-book-detail.xml \
  --harmony-layout /tmp/harmony-book-detail.json \
  --android-logical-width 815.2866 \
  --harmony-logical-width 816 \
  --title 'PUBLIC_FIXTURE_TITLE' \
  --series 'PUBLIC_FIXTURE_SERIES' \
  --volume 'PUBLIC_FIXTURE_VOLUME_AND_PAGES' \
  --size-value 'PUBLIC_FIXTURE_SIZE' \
  --format-value 'PUBLIC_FIXTURE_MEDIA_TYPE' \
  --min-score 98
```

Both captures must use the same book, locale, theme and logical width. Reading
progress and download state may legitimately differ between installations, so
the gate excludes state-dependent vertical anchors while still comparing the
read-control dimensions and stable file-information row stride. It also
normalizes content positions against each platform's destination area so an
Android-only landscape safe area does not become false drift. The current
private QA fixture passes at `98.9440%`; its dumps and screenshots remain in a
temporary directory and must not be committed.

`visual_parity.py --scan-axis horizontal` is also available for image regions
whose horizontal surfaces do not contain text at the chosen sample line.

## Current validation boundary

The native online/offline reading path, panel detection, color correction, and
MangaJaNai 2x MindSpore enhancement are implemented. The super-resolution
model is bundled under CC BY-NC 4.0; see `MODEL_NOTICES.md`, and replace it for
commercial distribution.

The local HarmonyOS simulator can validate navigation, persistence, image/PDF
reading primitives, and UI tests, but Reader Kit does not support the emulator.
EPUB, NNRT/NPU performance, background-task durability, signed installation,
and the 30-minute memory run must therefore be completed on a HarmonyOS 6 real
device before the build is advertised as fully validated. Release signing and
AppGallery checks are also intentionally outside unsigned local builds.
