# Android 0.27.0 release verification

## Artifact and scope

Application/test source: `fbdcc82022daa725bda690b13142d7cd87a255ad`, based on published `v0.27.0-beta.3`. This record is a documentation-only follow-up. The release is stable, not beta.4; no main-branch merge or issue closure is included.

- Package: `io.github.zhengningning.komelia`, version `0.27.0`, versionCode `54`.
- APK SHA256: `4fe6b059fdaee0d0e189604d215f5f23588c147a3d95a260b6008f416c8ab2b2`.
- Signing certificate SHA256: `32165c9f9f19f76e48a9434aedc4db4d9a9420df2259b12af9fa38ec704a1bfb`, matching published beta.3.
- Debug APK SHA256: `668963b980ffcef9c48f2294349c627ef62e2f0b6cc291602b5f2c0bcec983d1`.

Compared with stable 0.26.0, the release includes compatible settings, permission and login capabilities carried by the beta series. Repository SemVer rules therefore select 0.27.0 rather than 0.26.1.

## Reproductions and fixes

- TTU's fixed-height app container became a second vertical scroller through `overflow-x: hidden`. The old browser test moved that container by 700 pixels while document/window scroll stayed zero. Horizontal clipping now leaves scrolling with the document used by bookmarks.
- Image-reader entry trusted a stale navigation snapshot. The old regression restored page 1 despite saved page 2. Entry now fetches the current book through the selected content API.
- Destination models survived logout and reused the previous server's catalog. Session-scoped navigation identities and explicit session-end disposal now retire them. Temporary reader entry still retains the return stack; disposing nested navigation on every hide was rejected because it broke Back navigation.
- Final candidate testing found a separate stale detail percentage: green page 2 reopened correctly, but details still showed 33%. Two old-failing regressions now require fresh progress both on initial detail entry and on return without an event. Listeners are registered once. Final signed testing shows 33% for page 1 and 67% for page 2.
- An initial version of the new test cancelled Voyager's shared fallback scope and broke subsequent login tests. The test now uses an isolated registered model lifecycle. The full UI JVM suite then passed; the failed run is retained separately.

## Final checks

Operator: Codex, 2026-09-18. Dedicated synthetic-data Android emulators; no reporter device or private server.

| Layer | Actual result |
|---|---|
| UI/core/shared/offline/SQLite automated suites | 479 tests: 478 passed, 1 optional original-archive acceptance skipped |
| Harness, runner and synthetic fixture Node tests | 22 passed |
| Komga EPUB tests | 16 passed |
| TTU real Chrome scrolling regression | 1 passed |
| Svelte check | 0 errors, 0 warnings; outdated Browserslist advisory remains |
| Android debug/release, Desktop JAR, Wasm production matrix | Passed; final run 6m48s, 784 tasks |
| API 35 debug instrumentation | 23 passed, 1 original-archive acceptance skipped |
| API 37 debug instrumentation | 23 passed, 1 original-archive acceptance skipped |
| API 35 debug reader rotation | 10 portrait/landscape cycles, 20 correct transitions, no repeated hint; page 2 retained |
| API 37 signed reader rotation | Same 20 transitions, no repeated hint; page 2 and 67% retained |
| API 37 signed RAR reading | Red page 1 / green page 2 restored, detail percentages refreshed, Back returned to detail |
| API 37 signed TTU | Scroll then Back/reopen restored paragraph 28; paragraph-anchor restoration, not exact pixel restoration |
| API 37 signed server switching | A/B/A/B showed only the current synthetic server's catalog; the fourth login required correcting a test-driver input error |
| Upgrade/signature | In-place install succeeded with original first-install time and granted synthetic folders retained |

API 35 uses `Komelia_Regression_API35`; signed API 37 uses `Komelia_Regression_RC_20260918` with 16 KiB pages. Both use 1080x2400 portrait / 2400x1080 landscape. API 37 debug instrumentation uses `Komelia_Regression_API37`. The upgrade test retained beta.3 data through local intermediate candidates; it is not a claim of a separately reset direct-upgrade run.

Raw local evidence is retained in the task's `komelia-fix-release.jl4plZ` directory: `stable-final-matrix-2.log`, `final-native35.log`, `final-native37.log`, `final-reader.log`, `final-rar-page*-saved.json`, `final-release-rar-*.png`, `final-ttu-*.png`, and `rotation-emulator-*-final-reader/results.json`. The first rotation attempt landed on Home and was excluded; only the reader-specific rerun counts. Earlier candidate hashes and failures remain separate.

`final-login-cycle.log` records the first three server switches. On the fourth, the driver left trailing port digits; the app rejected the invalid port before sending a login. After clearing and verifying the input, `final-login-b-corrected.json` and its screenshot show only `QA-B`. This is a corrected-input completion, not an uninterrupted four-cycle automation pass. The final signed device's crash buffer was empty.

## Limits

- This targeted final-artifact record does not relabel the earlier 21-case full-QA record as fully passed.
- #62 persistent panel blur remains unresolved. Original-sample diagnosis and continuous-window improvements are documented in the earlier reader verification record; the complete original panel/webtoon matrix was not repeated on this final APK.
- No reporter phone, Windows desktop GUI, or physical-device acceptance was performed. Desktop/Wasm builds are not GUI acceptance. Web A/B switching evidence predates the final detail-refresh change and is not labeled final-artifact acceptance.
- Original/reference archive tests are skipped where their required external fixture is absent. Synthetic RAR rendering does not replace that comparison.
- No beta.4 binary, signing material, original comic content, or test-server credentials belong in published assets.
