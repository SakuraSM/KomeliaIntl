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
4. Unchanged size and modified-time pairs are skipped; changed and new files are inspected; missing records are removed without deleting source files.
5. Image/PDF pages and EPUB resources are served through the existing offline reader APIs. Local EPUB manifest links are resolved to the internal book-resource route before reaching either EPUB reader.
6. Startup scanning and Android WorkManager discover later changes according to the stored scan interval.

Risk points: revoked folder permissions, unstable IDs after moving files, archive path encoding, relative EPUB resources, duplicate scans, unsupported desktop PDF extraction, and accidentally treating local source files as app-owned cache.

## Reader navigation

### HarmonyOS local-content route

1. Login offers local-only mode without a Komga account. The selected destination is restored from Preferences without changing existing enum values or deleting saved server credentials.
2. The picker grants a directory where supported. Devices without durable folder grants import selected files into app-owned storage instead.
3. `LocalLibraryIndexer` completes the listing before replacing the index. Permission/listing failures retain the previous index; removal/exclusion deletes progress before book rows inside one transaction.
4. `AvailableContentRepository` exposes local books and the current account's completed downloads to Local, Home, and unified search. Search requests use the same modified-date order as their merge comparator and report partial coverage.
5. Local reader routes use native image/PDF/EPUB adapters and `LOCAL_PROGRESS`, never Komga progress or the server outbox. Index revisions invalidate retained local reader images on reopening.
6. Startup, return-to-foreground, and a foreground timer scan according to preferences. This is not an OS background job and does not promise scans while the process is stopped.

Download task cancellation retains the record and reusable files. Explicit task removal distinguishes retaining files from deleting both completed and partial downloads. Additive schema v10 stores task titles, byte progress, and speed; guarded writes cannot restart paused/cancelled tasks.

### Shared reader route

1. Detail or library navigation opens an image, PDF, or EPUB reader with a stable content/progress identity.
2. The reader distinguishes tap zones, drag/swipe gestures, controls, and system navigation.
3. Progress updates through the existing protocol without a gesture triggering multiple page or stack changes.
4. Back first closes the top reader overlay, then exits the reader once, then follows the application stack.

Risk points: duplicate Back handlers, click-through overlays, drag-end taps, stale progress, system-edge conflicts, and unsafe-area overlap.

## Localization

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
