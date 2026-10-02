# Android 0.28.0 release verification

## Artifact and scope

Feature commit: `79dcda3bb85bcbe9dbd4b4f216a2d78557dff178`, based on `0c9e7e862bfb99d785e98976b1140dbcb06a9af3`. The release commit adds the four version declarations and this record. The tested binary already contains those version declarations. No application source changed after the final build.

- Package: `io.github.zhengningning.komelia`, version `0.28.0`, versionCode `55`.
- APK SHA256: `27adc0a2e109e4c072a64da1b22468b6a1304dc19cdd671df3a5584c4312bb01`.
- Signing certificate SHA256: `32165c9f9f19f76e48a9434aedc4db4d9a9420df2259b12af9fa38ec704a1bfb`, matching published 0.27.0.
- Debug APK SHA256: `50ca70d521e4c8ca3dd043e77c4cde80928b4b7c2b861749e1c281ea74f90541`.

The new global setting is a compatible feature, so repository policy selects a minor release. This publication does not merge the feature branch into main or close issues.

## Final automated checks

Operator: Codex, 2026-10-02. All fixtures used here are synthetic.

| Check | Result |
|---|---|
| UI, core, SQLite, shared and offline `allTests` | 504 tests: 503 passed, 1 optional original-archive acceptance skipped |
| Harness, regression runner and fixture Node tests | 22 passed |
| Android debug and signed release, Desktop JAR, Wasm production build | Passed in 3m52s; 784 tasks |
| API 37 debug native instrumentation | 24 reported by runner: 23 passed, 1 optional original-archive acceptance skipped |
| Harness and whitespace checks | Passed |
| Release policy, version and bilingual notes checks | Passed for minor 0.27.0 to 0.28.0 |

The build command ran `:komelia-ui:allTests :komelia-domain:core:allTests :komelia-infra:database:sqlite:allTests :komelia-app:shared:allTests :komelia-domain:offline:allTests androidDebug desktopJar komfWebUI :komelia-app:androidApp:assembleRelease :komelia-app:androidApp:assembleDebugAndroidTest`, with required release signing, no configuration cache and two workers. Signing inputs stayed outside version control. Existing Gradle deprecation and webpack warnings remain.

Tests cover policy precedence, SQLite V16-to-V17 migration and rollback, both preset snapshot types, IndexedDB legacy records and reopen, old browser settings JSON, save failure and cancellation, editor identity, rendering invalidation and grayscale image ownership. Earlier tests reproduced the stale editor identity and prematurely closed grayscale result before their fixes. This turn also routed the editor error-page exit through the same disposal path.

## Signed APK emulator acceptance

`Komelia_Regression_RC_20260918`, API 37, arm64, 16 KiB memory pages. Portrait is 1080x2400 and landscape is 2400x1080. The separate debug instrumentation AVD is `Komelia_Regression_API37`.

The installed baseline was the published 0.27.0 APK, verified by SHA256 `4fe6b059fdaee0d0e189604d215f5f23588c147a3d95a260b6008f416c8ab2b2`. Before upgrade, the old UI saved a levels preset named `QA-Legacy` and set QA-Rar's output black level to 100. In-place installation retained the original first-install time, 2026-09-18 10:28:29. No uninstall or app-data reset was used.

| Scenario | Observed result |
|---|---|
| Upgrade migration | Global correction initially Off; legacy QA-Rar remained Custom with output black 100; saved preset remained available |
| Existing data | Synthetic local folders remained readable; QA-Rar reopened green page 2 and detail showed 67% |
| A-to-B editor isolation | QA-Rar showed Custom; QA-Zip showed Use global default in the same process |
| Global preset | QA-Zip rendered the copied levels settings |
| Per-book Off | QA-Zip returned to its original red pixels; reopening its editor retained Off |
| Return to inheritance | QA-Zip recovered the global correction, including after force-stop and restart |
| Rotation | One portrait-to-landscape-to-portrait cycle retained the inherited correction |
| Global Off versus custom | QA-Rar retained its custom effect after global correction was disabled |
| Localization | English and Simplified Chinese global settings and three book-policy labels rendered correctly |
| PDF smoke | Synthetic PDF rendered saved page 2 of 3 |
| EPUB smoke | Synthetic EPUB rendered its pre-upgrade bookmark at paragraph 28 |
| Artifact identity | Installed APK SHA256 equalled the frozen publication APK |
| Crash buffer | Empty after final signed testing |

Recorded center RGB pixels provide a rendered-output check, not just a selected-label check:

- QA-Rar before and after upgrade, and with global Off: `(136, 222, 136)`.
- QA-Zip with inheritance, after restart, and in both orientations: `(234, 136, 136)`.
- QA-Zip with per-book Off: `(220, 60, 60)`.

Some driver actions raced screen transitions or used an incorrect translated label. Those attempts stopped on missing targets and were repeated against the visible screen. `zip-reinherit-reader.png` and `zip-off-confirmed.png` are the final reader captures; the earlier `zip-reinherit.png` still shows the editor and is not reader acceptance.

Local raw evidence is retained under ignored `output/stable-release-0.28.0/`: the matrix log, native report and log, UI dumps and synthetic screenshots, plus the exact APK and checksum. Signing inputs are excluded.

## Limits

- API 35 acceptance was blocked by the missing emulator system image. No physical phone, Windows GUI, or desktop GUI acceptance was performed.
- This targeted final-artifact verification does not mark all 22 regression-catalog cases as passed. The earlier ten-cycle rotation matrix and full original-panel matrix were not repeated.
- #62 fast-scroll and tap blur remains unresolved. Synthetic color tests do not establish a fix on the reporter's device or original samples.
- Remote server login, original archive comparison and native grayscale rendering with an RGB preset were not repeated on this signed APK. The grayscale ownership fix has an automated regression; it is not claimed as signed-device coverage.
- EPUB source is unchanged. This run checks opening and bookmark retention, not a new complete EPUB browser test matrix.
- Android is the published package. Desktop and Wasm compilation are not release-package or GUI acceptance.
