# Bounded contexts and contracts

| Context | Owned state and behavior | Stable boundary | High-risk changes |
|---|---|---|---|
| Server session | Komga base URLs, authentication/session state, connectivity selection | Credentials stay local; primary address survives LAN failure | URL migration, auth persistence, connectivity races |
| Library/catalog | Libraries, series, books, collections, read lists, filters, Home groups | Komga API identity and ordering semantics | stale selection, filter persistence, duplicate state owners |
| Reader | Image/PDF/EPUB navigation, progress, chrome, retries | progress protocol and Back behavior | gesture overlap, double navigation, content loss |
| Offline | download lifecycle, cache index, local browsing, deletion, logs | cache/database compatibility and privacy | orphaned files, silent errors, destructive cleanup |
| Presentation | adaptive navigation, layout tokens, themes, locale, accessibility | persisted theme/language values and shared design tokens | platform divergence, inaccessible overlays, overflow |
| Persistence | transactions, schema, settings, SQLite/Wasm implementations | previous schema and serialized values | migration failure, partial writes, target mismatch |
| Native media | decoders, color/inference, JNI, WebView resources | architecture-specific packaging | missing libraries, license drift, host/target confusion |
| Komf extension | browser extension app and content/background/popup behavior | extension manifest and browser packaging | browser permission or bundle differences |
| Distribution | versions, signing, tags, bilingual notes, assets, issue closure | immutable tags and package identity | wrong version, wrong repository notes, unverified asset |

## Repository boundaries

- `third_party/` and submodule revisions are dependency inputs. Modify them only for explicitly scoped dependency work with license and build review.
- `build/`, `dist/`, and staged Release artifacts are generated outputs, not source-of-truth edits.
- Local SDK paths, credentials, signing material, test server data, and private media stay outside the repository.
- Android package identity and signing determine upgrade compatibility; changing either is a distribution decision.
- Release publication, pull-request merge, and issue closure are external state changes and require explicit authorization.

## Cross-cutting contracts

- English and Simplified Chinese resources change together for user-visible behavior.
- Light, Dark, and OLED retain persisted values and must remain readable.
- Compact, Medium, Expanded, and Full layouts share state; resizing must not reset navigation or selection.
- Reduced motion, Back/Esc, focus restoration, and safe areas apply to all first-party overlays and navigation.
- Upstream synchronization must reapply fork-specific localization, remote/LAN switching, offline support, package/release metadata, and reader fixes deliberately.
