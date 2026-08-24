# Version releases

Komelia Intl uses stable Semantic Versioning values in the form `MAJOR.MINOR.PATCH`. The Git tag adds a `v` prefix.

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
```

Run the policy tests and preflight checks before you create a tag:

```shell
scripts/test-release-policy.sh
scripts/check-release-version.sh \
  --tag v0.18.17 \
  --previous 0.18.16 \
  --level patch
scripts/check-release-notes.sh \
  --file /path/to/release-notes.md \
  --version 0.18.17 \
  --title "Komelia v0.18.17" \
  --level patch
```

The scripts reject skipped versions, a level that does not match the numeric increment, mismatched application version files, a nonstandard Release title, and invalid language sections.

## Publish without changing a released version

Create a draft Release first. Attach every listed package and checksum, then run the checks. Publish only after the assets and notes match. If a published package needs a correction, increment PATCH and publish a new Release. Do not replace a published tag or binary.

References:

- [Semantic Versioning 2.0.0](https://semver.org/)
- [GitHub automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
