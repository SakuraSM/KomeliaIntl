# Android 0.28.1 release verification

## Artifact

Fix commit: `85031bb0260957df670fba0534f9bed734ec401a`. The release commit adds the four version declarations and this record. See [the local-series diagnosis](local-series-empty-list-20261002.md) for old-code failures and the fix.

- Package: `io.github.zhengningning.komelia`, version `0.28.1`, versionCode `56`.
- APK SHA256: `5f517c135af15a87004e99b29b9acf4d91aae0c4a824d83dc1a557f251953a85`.
- Signing certificate SHA256: `32165c9f9f19f76e48a9434aedc4db4d9a9420df2259b12af9fa38ec704a1bfb`, matching 0.28.0.
- Signature, version metadata and 16 KiB APK alignment checks passed. The installed application is not debuggable.

## Automated checks

Operator: Codex, 2026-10-02.

| Check | Result |
|---|---|
| UI, SQLite, offline, core and shared `allTests` | 508 tests: 507 passed, 1 optional original-archive acceptance skipped |
| Node harness, runner and fixture tests | 22 passed |
| Desktop JAR, Wasm production and signed Android release | Passed |
| Final build including the separate test-helper APK | Passed, 12s, 724 tasks; reused earlier completed tasks |
| Harness, whitespace, release policy, patch version and bilingual notes | Passed |

The build reran `:komelia-ui:allTests :komelia-infra:database:sqlite:allTests :komelia-domain:offline:allTests :komelia-domain:core:allTests :komelia-app:shared:allTests desktopJar komfWebUI :komelia-app:androidApp:assembleRelease :komelia-app:androidApp:assembleReleaseAndroidTest`, with required release signing, private init scripts, no configuration cache and two workers.

The temporary helper is a separate, same-certificate instrumentation APK. Its source and configuration stay outside the repository and published assets. Initial helper attempts failed on test-only shrinker dependencies and an AndroidJUnitRunner call into optimized AndroidX tracing. A platform-only instrumentation entry point completed fixture setup and restoration without changing the application APK hash. These setup attempts are not product acceptance passes.

## Final signed-package emulator checks

Dedicated AVD `Komelia_Regression_RC_20260918`, API 37, arm64, 16 KiB memory pages, 1080x2400, Simplified Chinese. Only synthetic test content was used.

1. Verified that the installed 0.28.0 baseline matched published SHA256 `27adc0a2e109e4c072a64da1b22468b6a1304dc19cdd671df3a5584c4312bb01`.
2. Installed the final 0.28.1 APK directly over that baseline. First-install time remained 2026-09-18 10:28:29. Existing folder grants and QA-Rar's page-2 progress at 67% survived. The rendered green page retained its custom correction, with center RGB `(136, 222, 136)`.
3. Seeded and selected a synthetic non-root offline server user through the separate helper, then force-stopped and restarted the unchanged signed app. The account screen confirmed that user.
4. Opened a local book's multi-book series. All six books were listed.
5. Opened the synthetic single-book series from the library's series grid. Its book details and Read action appeared, and the comic rendered.
6. Force-stopped and restarted the app with the same non-root account, then reopened the single-book series successfully.
7. Restored the previous account through the helper. The final manual-acceptance crash buffer was empty; the earlier test-runner crash was preserved separately before clearing that buffer.

The installed APK and frozen publication APK have the same hash. No data reset, application uninstall, database migration or source-book modification was needed. The temporary copied comic and helper are excluded from publication.

Raw local evidence is retained under ignored `output/stable-release-0.28.1/`, including screenshots, UI dumps, build logs, helper-result logs and the frozen APK with checksum. Signing inputs and helper binaries are excluded.

## Coverage

This record covers the final signed Android package on API 37, not a reporter phone. Desktop and Wasm were compiled, not manually exercised. Empty-state retry and exit have automated and debug-emulator coverage in the linked diagnosis record; the final signed run focused on the account-filtering failure and upgrade compatibility.
