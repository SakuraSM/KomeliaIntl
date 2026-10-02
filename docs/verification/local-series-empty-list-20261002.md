# Local-series navigation verification

## Cause and fix

The reported screen displayed `NoSuchElementException: List is empty.` after opening a local series. The reproduced condition was a selected non-root offline server user and a local single-book series.

`AvailableBooksRepository` lists device books using the root catalogue. Series navigation instead queries `OfflineBookApi` using the selected offline user. SQLite restricted that list to the user's server libraries, excluding device-local libraries. The book remained in the database and could be opened directly, but the series query returned no books. `OneshotViewModel` called `first()` on that result.

The fix adds device-local libraries to the selected user's book-list visibility. Remote downloads retain their existing server restriction. Count and page queries share the same predicate, and reading progress still uses the selected user rather than switching to root.

Single-book series accept an empty result and show the existing localized `No books` message with Reload and Exit. Manual and event-driven reloads use the same safe path. Request failures remain errors, distinct from an empty result.

## Source and artifact

- Base: `c5c56e54596c2981dc282b816033396b4c3e41d1`.
- Branch: `fix/local-series-empty-list`.
- Package: `io.github.zhengningning.komelia.debug`, version `0.28.0-debug`, versionCode `55`.
- Final debug APK SHA256: `49fd3faff2961ff13a16775f1dd000cc5cd7293af86e98cb21b3162e99f7d704`.
- Previous debug APK SHA256: `50ca70d521e4c8ca3dd043e77c4cde80928b4b7c2b861749e1c281ea74f90541`.

This is a local debug candidate, not a new published release. No version bump, tag change, schema migration or source-book rewrite is included.

## Automated evidence

Operator: Codex, 2026-10-02. Fixtures are synthetic.

| Check | Result |
|---|---|
| SQLite old-code reproduction | Local import succeeded, root found the book, selected server user returned an empty list; matching `NoSuchElementException` recorded |
| UI old-code regressions | Both empty-result tests failed; a subsequent event-reload regression also failed before that path was unified |
| Final UI, SQLite, offline, core and shared tests | 508 tests: 507 passed, 1 optional original-archive acceptance skipped |
| Node harness, regression runner and fixture tests | 22 passed |
| Android debug, Desktop JAR, Wasm production matrix | Passed, 8m11s, 566 tasks |
| Final full UI suite after strengthening retry assertions | Passed, 7s, 223 tasks |
| Harness and whitespace checks | Passed |

SQLite tests exercise real imported single-book and multi-book series, page counts, ordering, read-status filters, selected-user progress, root access and exclusion of another server's downloads. UI tests distinguish failed requests from empty results and recover an empty screen to a real book after retry.

The matrix command was `./gradlew :komelia-ui:allTests :komelia-infra:database:sqlite:allTests :komelia-domain:offline:allTests :komelia-domain:core:allTests :komelia-app:shared:allTests androidDebug desktopJar komfWebUI --no-configuration-cache --max-workers=2`. The final UI rerun used `:komelia-ui:allTests`. Existing native libraries and EPUB dependencies were reused from the same source baseline. No native decoder source changed.

## Android evidence

Dedicated AVD: `Komelia_Regression_API37`, API 37, arm64, 1080x2400, Chinese dark theme. A synthetic non-root server user was added to the debug app's test database. The original database was backed up before this fixture change.

1. The previous debug APK showed the same error page when opening the synthetic single-book series. Reload reproduced the error.
2. The final APK was installed in place without clearing app data or changing the selected user. The same series showed its book details and Read action.
3. The account screen confirmed that the synthetic non-root user remained selected.
4. Opening a local book's series showed all seven books in the multi-book series.
5. A synthetic comic rendered after opening it from that series. Force-stop, restart and re-entry into the single-book series succeeded.
6. A separate stale-empty-series fixture rendered `暂无书籍`. Reload kept the empty state without a toast, and Exit returned to the previous screen.

After verification, the original test database was restored and the account screen again showed root. Temporary fixture copies were removed. Source books were not changed. The final device crash buffer was empty.

Raw logs and synthetic screenshots are retained locally under ignored `output/local-series-empty-list-20261002/`. Fixture databases and backups are not included in the repository or test package.

## Coverage boundary

The device check used the debug package on API 37, not the reporter's phone or a new signed release. Desktop and Wasm were compiled, not manually exercised. The skipped archive test requires an external original sample. The generic frontend lint script found no applicable TypeScript, Vue or Python targets and is not Kotlin validation.
