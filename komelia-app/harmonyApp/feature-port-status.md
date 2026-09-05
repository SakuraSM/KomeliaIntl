# HarmonyOS feature port, 2026-09-05

## Outcome and scope

This change ports the main `98e0204b` local-content workflow into the independent ArkTS application on `feat/harmonyos-main-sync-20260905`. It includes the native foundation required by the feature port. Implementation and validation used an isolated worktree. Publication is limited to the requested source commit and branch push; no Release, signing-profile change, device installation or original-worktree update is included.

| Main behavior | HarmonyOS implementation |
| --- | --- |
| Local-only startup and navigation | `AppState`, Login, MainShell and primary navigation; existing destination values retained |
| Local libraries, refresh, exclusion and removal | Native picker, capability checks, bounded archive indexing, startup/foreground-hourly scans, exclusion/restore, transactional metadata/progress cleanup |
| Local and downloaded browsing | Source filter, title query, sorting and pagination; completed downloads restricted to the active server/account |
| Unified Home/search | Local/download Home groups, continued/recent/next reading, local series; search modified-date merge, ID deduplication and partial-source coverage |
| Reading | Local image/CBZ/PDF pages and native Reader Kit EPUB; local progress stored separately and never uploaded to Komga |
| Download task center | Persisted title/bytes/speed, paused/cancelled late-progress guards, retained cancellation, explicit remove-task versus remove-task-and-files |
| EPUB display | Default safe-area layout, optional immersion, 8 vp default top spacing with 0/8/16/24/32/48 options; theme-matched margins and loading background |
| Search cards | Text-driven height above the existing minimum, without the fixed height cap |

Schema upgrades are additive: v8→v9 adds local library/book/progress tables; v9→v10 adds download title and byte-progress columns with backward-compatible defaults. Library removal/exclusion deletes progress before book rows. A reader finishing after index removal cannot recreate orphan progress.

## Verification

| Executed check | Final result |
| --- | --- |
| `bash scripts/harmony-test.sh` from `harmonyApp` | 129 Hypium/ArkTS tests passed, zero failures/errors/ignored; static native UI guards passed |
| `node --test scripts/local-content-schema.test.mjs` | 4 host SQLite cases passed: additive migration preservation, progress-before-book cleanup, rollback, and guarded byte writes |
| Hvigor `assembleHap --mode module -p product=default -p module=entry@default -p buildMode=debug --no-daemon` | Passed; final signed application HAP built in 4 s 663 ms, no device installation |
| `./gradlew :komelia-ui:allTests --offline` | Successful, JVM/Wasm test tasks `UP-TO-DATE`; previous outputs reused, not fresh test executions |
| `node scripts/check-harness.mjs` | Passed, 15 required files, 25 Markdown files, zero warnings |
| `node --test scripts/check-harness.test.mjs` | 6 passed |
| Resource-key comparison against pre-port snapshot | 38 new keys present in base, English and Simplified Chinese, no duplicate keys |
| Diff/staged whitespace, conflict-marker and unmerged-path checks | Passed; no `.gitmodules` change |

There were three stale seven-group assertions during development. They now assert the nine-group model while retaining disabled states and custom order; the final full run passed. SDK deprecation and API exception-handling warnings remain. Host SQL tests and ArkTS unit tests are different evidence from a device upgrade or real reading session.

The signed output is `entry/build/default/outputs/default/entry-default-signed.hap`. Logs remain in the local temporary directory as `komelia-port-tests.log`, `komelia-port-build.log` and `komelia-port-shared-tests.log`. Older `outputs/ohosTest` packages are not evidence for this feature pass. HAPs, logs and local signing configuration are not committed.

Final signed HAP SHA-256: `2acf99a9774c9f745d8d92ead834a3f5c2f7671db532cb431f7f67f3d121657b`.

Android/desktop/Wasm application package builds and device UI tests were not run in this code-only pass. No Kotlin, native decoder, submodule, or Gradle build input was modified by the feature port.

## Platform differences and pending runtime checks

- Android WorkManager has no equivalent wired here. Local scans run at startup and while the foreground process is active, respecting the stored interval. Returning to the app performs overdue scans.
- On devices without durable directory grants, imports are private snapshots. Re-import does not overwrite a file an active reader may be using; remove its library and import again to replace it.
- Native ZIP support is deliberately bounded. ZIP64, split/encrypted archives, non-ASCII legacy ZIP filename encoding, RAR/CBR and 7z are not implemented.
- EPUB stays on Huawei Reader Kit. Komga/TTU web-reader selection is not added. Native Reader Kit chapter parsing, progress restoration, immersion/cutout behavior, and ZIP inflation require runtime checks on a supported device.
- Android Lanczos3/Mitchell kernels and Compose pinch/tile-prefetch changes remain Android implementation details, not HarmonyOS parity claims. HarmonyOS keeps its existing Image Kit reader/gesture and AI pipeline.
- Device RDB upgrade with existing user data, persistent grant restoration/revocation, CBZ/PDF/EPUB reading, download interruption/restart/cancel/delete, bilingual layouts and safe areas remain pending. No private library was used in this code-only pass.
- Earlier cover-flicker, cold-start and physical rotation-lock acceptance remain separate, as do the existing device suite's skipped-authentication reliability problems. Earlier device-suite totals are not validation of this new HAP.

## Review boundaries

Task-owned changes are the native local-content models/services/repositories/pages, their reader/navigation/Home/search integrations, download progress/removal behavior, EPUB display options, bilingual resources, logic/SQL tests, and these map/status updates. Existing signing configuration, media models, icons, native ports and unrelated work are not newly authored by this change. Do not stage the worktree wholesale.
