# Android test package and physical-device validation

Use this workflow when handing an Android change to the user for physical-device validation. A test APK is a local review artifact; it is not a published Release.

## Prepare an isolated change

1. Follow the [change protocol](change-protocol.md): start from the agreed base in a task-owned worktree and preserve the original checkout and user changes.
2. Reproduce the reported behavior where practical, implement the change, and run the affected checks from the [testing guide](testing-guide.md).
3. Keep the worktree and patch available while the user validates. Do not merge, publish, or close issues merely because a test APK was delivered.

## Keep the test app identifiable and separate

- Build the `debug` variant with `./gradlew androidDebug` using the repository wrapper. Set `ANDROID_HOME` locally when a fresh worktree needs the SDK; never commit machine-specific SDK paths.
- Preserve the existing `.debug` application ID suffix and `-debug` version-name suffix. The test package is `io.github.zhengningning.komelia.debug`; the production package remains `io.github.zhengningning.komelia`.
- Override the launcher name only in `komelia-app/androidApp/src/debug/res`: `Komelia Test` in default/English resources and `Komelia 测试版` in Simplified Chinese. Leave production names and signing unchanged.
- Keep both locale overrides synchronized. A default-only override can leave the Chinese launcher showing the production name.
- Use debug signing for this workflow. Do not reuse production signing or change the production version just to label a test package.

## Verify the actual APK

1. Read the final APK using the installed Android SDK tools, not just the source configuration. With `aapt2 dump badging`, verify the application ID, debug version suffix, and default/Chinese application labels. With `apksigner verify`, verify the APK signature.
2. Install the final artifact on an available test device or emulator and cold-start it. When upgrading an existing test app, use `adb install -r` and retain its data. If signing differs, report the conflict; do not silently uninstall either app or erase its data.
3. Exercise the affected flow, including applicable compact layouts, themes, long content, and missing data. Record installation, launch, emulator checks, and physical-device checks separately. An emulator pass does not establish a physical-device pass.
4. Restore any temporary display, theme, and layout preferences changed during QA. Do not commit private screenshots, filenames, credentials, or server configuration.
5. Copy the verified APK to an ignored output directory with a descriptive task/date filename, and record its SHA-256. Deliver the same bytes that were checked; a later rebuild requires artifact verification again.

## Reader and search regression scenarios

- For PDF navigation, test a loaded document with actual left/right edge gestures and the visible three-button Back control. An injected `KEYCODE_BACK` alone does not prove gesture compatibility. First Back exposes PDF controls; the next Back while controls are visible exits. Re-enter the document and repeat, and verify the toolbar back arrow exits directly. Controls auto-hide after four seconds, resetting the confirmation.
- Keep Android system navigation available by default while hiding the reader status bar; EPUB can explicitly opt into full immersion from its settings. Hidden navigation uses `BEHAVIOR_DEFAULT` so edge Back remains available. Check actual edge Back in immersive mode, that leaving the reader restores the system bars, and that temporary reader controls do not block Back.
- For Komga EPUB, simulate a display cutout and verify both portrait top insets and landscape side insets, toolbar access, page navigation, and reader exit. Default settings automatically avoid system insets and add 8dp top spacing. Verify immersion on/off, extra spacing, restart persistence, and light/sepia/night background continuity. Theme changes must not recreate the native WebView or reset reading position. Toggle immersion on then off and reopen the same book without restarting the app to catch stale reader state. Confirm edge page-turn overlays are absent in scroll and paginated modes, while scrolling and tap-to-turn still work. Record the WebView bounds against the cutout bounds. Restore the original cutout, rotation, and navigation mode after testing.
- Local and global search use `SearchTextField`: verify 48dp mobile height with empty/long placeholders and a nonempty query, clear-button interaction, and the original filtering behavior. Keep English and Chinese clear-button labels synchronized.

## Update checks in test builds

- Standalone Android builds expose **App updates** and retain the persisted startup-check preference (enabled by default, throttled to once per 24 hours). Manual checks bypass the startup throttle.
- Keep update discovery separate from APK self-installation: `ENABLE_UPDATE_CHECKS` is enabled for standalone; the existing `ENABLE_SELF_UPDATES` flag still controls in-app installation. F-Droid and Play builds retain their distribution policies.
- Debug builds open the Release download page on a user update action. They must not install a production APK as though it were an update to the separate `.debug` package.
- Verify successful checks, an empty release response, network failure and retry, disabled startup checks, throttling, and dismissed-version behavior. Do not call a failed check “up to date.” A check or test-package request does not authorize installing a real production update during QA.

## Hand off and collect feedback

Provide a direct link or attachment to the APK, the visible app name, package ID, source worktree/branch, and a short list of what to check. Include completed checks and any unverified scenarios.

Explain that the test app can coexist with production and has separate app data. The user may need to configure a server or grant local folder access again. Do not request credentials in chat or imply that production data is automatically copied.

Mark physical-device acceptance as **pending user validation** until the user reports the result. For UI work, ask them to check the reported screens, cover sizing, spacing, long titles, and relevant themes. If it fails, collect device/OS, reproduction steps, and redacted evidence, then iterate in the same worktree and hand off a newly verified APK.

Local artifact handoff does not authorize an external upload, Release, merge, or issue closure. Follow explicit session authorization for those actions.
