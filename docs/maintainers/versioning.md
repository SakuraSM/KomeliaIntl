# Version releases

Komelia Intl uses Semantic Versioning. Stable versions use `MAJOR.MINOR.PATCH`; public test builds use `MAJOR.MINOR.PATCH-beta.N`. The Git tag adds a `v` prefix.

## Pre-release versions

- Use `beta.1`, `beta.2`, and so on for public test builds, up to `beta.998`. Do not publish an unlabeled test build.
- Use the next stable version as the numeric core. For example, a Beta for the minor release after `0.19.4` is `0.20.0-beta.1`.
- Increment `beta.N` for every replacement package. Never move an existing tag or replace a published binary.
- Mark the GitHub Release as a pre-release. The application version, Git tag, Release title, and package filenames must all include the same Beta suffix.
- A stable version has higher precedence than a pre-release with the same numeric core: `0.20.0-beta.2 < 0.20.0`.

## Pick the release level

Use the highest level required by any included change.

| Level | Use it for | Example |
|---|---|---|
| Patch | Backward-compatible bug fixes, localization corrections, and release-only maintenance | `0.18.16` to `0.18.17` |
| Minor | Backward-compatible user-facing features and substantial compatible improvements | `0.18.16` to `0.19.0` |
| Major | Backward-incompatible changes after the public contract is stable | `1.4.9` to `2.0.0` |

A release that contains both fixes and a compatible feature is a minor release. A release that contains any backward-incompatible change is a major release.

The project currently uses `0.x` versions. During this period, use a minor release for a backward-incompatible change and identify the change as breaking in both languages. Do not publish `1.0.0` without an explicit maintainer decision that defines the stable public contract.

## Classify common changes

- A fix for a GitHub Issue uses a patch release unless the fix intentionally removes or changes supported behavior.
- A new setting, reader mode, platform capability, or user workflow uses a minor release.
- A compatible database migration follows the user-visible change that requires it. A migration used only by a fix remains a patch change.
- A database reset, incompatible stored-data change, removed platform, or incompatible protocol change requires the breaking-change rule.
- Documentation and CI changes do not require a Release by themselves. If maintainers publish them with app changes, they follow the app change's level.

## Validate the version

Build the next patch version with the default command:

```shell
scripts/build-release.sh
```

For a minor or major release, pass the level:

```shell
scripts/build-release.sh --bump minor
scripts/build-release.sh --bump major
```

If you provide a version, it must match the selected level:

```shell
scripts/build-release.sh --bump minor --version 0.19.0
scripts/build-release.sh --bump minor --version 0.20.0-beta.1
```

Run the policy tests and preflight checks before you create a tag:

```shell
scripts/test-release-policy.sh
scripts/check-release-version.sh \
  --tag v0.20.0-beta.1 \
  --previous 0.19.4 \
  --level minor
scripts/check-release-notes.sh \
  --file /path/to/release-notes.md \
  --version 0.20.0-beta.1 \
  --title "Komelia v0.20.0-beta.1" \
  --level minor
```

The scripts reject skipped version cores, a level that does not match the numeric increment, malformed pre-release labels, mismatched application version files, a nonstandard Release title, and invalid language sections.

## Android APK distribution

Android builds produce one universal APK and one standalone APK for each of `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. They share the release application ID, version, version code, and signing identity. Debug APKs retain their separate `.debug` identity.

`scripts/build-release.sh --apk` builds and stages them under `build/android-release/<version>/`. Staging requires Android build tools from `ANDROID_HOME`/`ANDROID_SDK_ROOT` or `PATH`. It verifies package identity, version, ABI and every signature, and rejects differing signing certificates. Upload only `release/` to the existing GitHub Release: it contains the universal `Komelia-<version>.apk` and, when configured, `android-abi-apks.json`. Publish `abi/` at a separate immutable HTTPS directory, and link to those files in the release notes. Never upload the ABI APKs as additional `.apk` assets to the existing Release: older clients select its first `.apk`, even if they skip intermediate versions. Do not depend on release asset order.

Pass `--abi-base-url https://example.org/path/<version>/` when building the release to create the update manifest for that directory. Without it, staging omits the manifest and clients continue using the universal APK. `SHA256SUMS` in the staging root records all staged files; merge the appropriate checksums into the Release's full checksum file. Do not upload the manifest before every referenced APK is available at its final URL and its downloaded bytes match the recorded hash.

Verify each signed output's package ID, version, certificate, ABI and required native libraries. Compare the certificate with the previous Release or an approved signing lineage; matching the new APKs to each other is not enough. Check the 64-bit ZIP and ELF 16 KiB alignment and install the final artifacts on the relevant devices or emulators. Test an upgrade from the previous signed Release, including the path where an old client skips the transition release. A debug APK cannot replace a release APK. Google Play bundles use `--bundle --variant play` and retain the app bundle's versioning and Play signing rules. Run APK and bundle builds in separate Gradle invocations: AGP 9.1.1 cannot reuse the release resource-shrinker outputs from ABI-split APKs when building an app bundle.

## Publish without changing a released version

Create a draft Release first. Attach every listed package and checksum, then run the checks. Publish only after the assets and notes match. If a published package needs a correction, increment PATCH and publish a new Release. Do not replace a published tag or binary.

References:

- [Semantic Versioning 2.0.0](https://semver.org/)
- [GitHub automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
