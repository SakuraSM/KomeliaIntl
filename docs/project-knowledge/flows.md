# Stable flows and risk points

## Application data flow

1. A platform application assembles database, network, WebView, decoder, inference, and shared app dependencies.
2. Shared application/UI code requests behavior through domain repositories and state holders.
3. Domain code calls the Komga API, offline contracts, transaction boundary, decoder, or inference API.
4. Infrastructure implementations persist or execute the platform-specific work.
5. Presentation state renders loading, data, empty, offline, and error outcomes.

Risk points: duplicate state owners, platform-specific behavior leaking into common code, failed requests represented as empty data, and persistence changes without migration tests.

## Remote and LAN selection

1. Load the primary remote URL and optional LAN URL from settings.
2. When automatic switching is enabled, probe the LAN endpoint without replacing the primary configuration.
3. Use LAN while reachable; otherwise use the primary remote endpoint.
4. Re-evaluate when Android connectivity changes and when the relevant app lifecycle restarts.
5. Preserve authentication and expose failures without logging secrets or private addresses.

Risk points: probe races, stale active URL, destructive settings rewrites, and treating authentication failure as network unavailability.

## Offline download and reading

1. A user requests a download for a supported book/media type.
2. Offline domain state records intent and progress while platform storage writes the file.
3. Completion links the cache record and local file for browsing and reading.
4. Offline mode resolves supported content locally and records useful redacted information/errors.
5. Deletion removes both record and owned file safely; restart reconciles partial or stale state.

Risk points: partial files, cancellation, record/file divergence, missing error details, deleting paths not owned by Komelia, and format-specific reader differences.

## Local folder libraries

1. A native app can prepare a local-only root user and media server, allowing startup without Komga credentials.
2. The user grants a folder; Android persists the Storage Access Framework tree permission and desktop keeps the selected platform path.
3. `LocalLibraryManager` recursively lists supported files, creates stable library/series/book identities from relative paths, and stores metadata in the existing offline database.
4. Unchanged size and modified-time pairs are skipped unless a local EPUB has an older inspection version. EPUB inspection imports EPUB 3 navigation or EPUB 2 NCX, falls back to the spine when no TOC exists, and persists a byte-weighted position index. Reinspection keeps book IDs, metadata locks and reading progress; missing records are removed without deleting source files.
5. Image/PDF pages and EPUB resources are served through the existing offline reader APIs. Local EPUB manifest links are resolved to the internal book-resource route before reaching either EPUB reader.
6. Startup scanning and Android WorkManager discover later changes according to the stored scan interval.

Risk points: revoked folder permissions, unstable IDs after moving files, archive path encoding, relative EPUB resources, duplicate scans, unsupported desktop PDF extraction, and accidentally treating local source files as app-owned cache.

Android import and archive reading share `AndroidZipArchiveOpener`. Seekable files are read directly. Providers that cannot seek or expose a usable length use an app-private temporary copy. `ArchiveScratchSpace` shares reservations across import and reading, with 1 GiB per file, 2 GiB total, and a 128 MiB free-space reserve. Cancellation, parse failure, and reader-cache eviction release owned copies; initialization removes only this feature's orphan files. Permission and corrupt-ZIP failures are not copy-retry triggers. Per-book import failures remain visible in the local-folder screen and offline logs.

Local chapter labels and `metadata.numberSort` preserve decimal values such as `1.5`. The integer book position stays separate. Rescanning unchanged books repairs unlocked metadata without reopening archives or changing book identity and progress.

## Reader navigation

Online Komga sibling navigation uses the remote chapter directory even when the current book has cached content. Local-source books and explicit offline mode use the offline directory. Online read lists keep their own order; the existing offline series fallback remains unchanged. A sibling lookup or page-list failure is a retryable state, not the end of a series. Retrying updates neighbouring books without resetting the current book, page, or zoom.

1. Detail or library navigation opens an image, PDF, or EPUB reader with a stable content/progress identity.
2. The reader distinguishes tap zones, drag/swipe gestures, controls, and system navigation.
3. Progress updates through the existing protocol without a gesture triggering multiple page or stack changes.
4. Back first closes the top reader overlay, then exits the reader once, then follows the application stack.

Paged image loads belong to an explicit window of up to five spreads and ten pages. Changing the visible spread cancels its presentation wait, while image loading and crop processing remain reusable inside that window. Eviction or reader shutdown closes the owned load; a stopped window cannot accept late loads. Android published tile bitmaps remain owned by any outgoing painters/render frames until those references are released, rather than being manually recycled on replacement.

Native decode, crop replacement, and resize operations share an image mutex across suspensions. Coroutine cancellation requests shutdown, but native images and source handles are released only after processing jobs complete. A single-parallelism dispatcher is not a substitute for that ownership boundary.

Static tiled pages publish a bounded whole-page preview before waiting for high-resolution tiles. The preview's longest edge is at most 768 pixels, it is reused across zoom and viewport changes, and crop/source replacement creates a new preview. Frame publication owns both preview and tile pixels; page shutdown releases that ownership through the platform retirement policy. Preview pixels fill only gaps in the loaded tile geometry so translucent content is not composited twice. Small full-frame images and animated images do not create this additional preview.

Risk points: duplicate Back handlers, click-through overlays, drag-end taps, stale progress, system-edge conflicts, and unsafe-area overlap.

Local EPUB positions describe approximate reading progress, not physical pages. Komga reads the cached positions service without scanning the publication before first paint; local progress matching accepts internal absolute resource URLs and archive-relative locators. Chapter scroll handoff is armed only by a single-finger vertical gesture and may finish after momentum settles. Touches originate inside the iframe, but the SDK scrolls the outer `main#iframe-wrapper`; use screen coordinates for gesture distance and the wrapper for scroll events and boundaries. It is disarmed after one navigation, cancellation, expiry or resource replacement, so initial short chapters cannot auto-skip.

## Server announcements and application updates

Server settings announcements use only the active Komga session's `KomgaAnnouncementsApi`. A failed server request is an error, not an empty feed or a fallback to GitHub release notes. Application version checks and Komelia release notes remain under App settings / App updates. Keep the existing server-admin navigation gate and read-status API unchanged.

## Localization

Poster grids share `posterGridCells`, including loading placeholders and the settings preview. Compact and medium mobile layouts use the existing card-width preference as density: the default 240 gives three columns, bounded by actual width and touch-target size. Larger mobile layouts, desktop, and Web retain adaptive minimum widths. No additional preference or migration is needed.

Settings updates serialize the read-transform-save-publish sequence. Card-size drag events coalesce to the latest value and finish an in-flight save when leaving the settings screen. An earlier database write must not overwrite the final slider position or another preference.

1. Persist `SYSTEM`, `EN`, or `ZH_CN` using the stable setting values.
2. Apply locale before the root resource environment is composed; Wasm may reload after persistence.
3. Resolve first-party Compose and EPUB control strings in the selected language.
4. Verify layout with long English and Chinese strings and with missing/empty metadata.

Risk points: raw enum/API values shown to users, duplicated string systems, stale resources, and dynamic lists that bypass localization.

## Pull request to Release

1. Implement and verify a task in an isolated branch/worktree.
2. Open a pull request with user-visible outcome, issue reference, SemVer impact, and actual evidence.
3. Review and merge only after acceptance criteria and required checks pass.
4. Select the highest required SemVer level and update every version source.
5. Create a draft Release with concise Chinese and English modules.
6. Build, upload, inspect, install, or launch only the packages in scope.
7. Publish after tag, version, title, notes, and assets agree.
8. Reply in each reporter's language and close fixed issues only after the relevant published assets are verified.

Risk points: merged code described as released, stale About version, wrong repository changelog, mutable tags, untranslated notes, and asset presence mistaken for runtime verification.
