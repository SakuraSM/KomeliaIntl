#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
repository_dir="$(cd "${project_dir}/../.." && pwd)"

catalog_version="$(sed -n 's/^app-version = "\([^"]*\)"/\1/p' "${repository_dir}/gradle/libs.versions.toml")"
harmony_manifest_version="$(sed -n 's/.*"versionName": "\([^"]*\)".*/\1/p' "${project_dir}/AppScope/app.json5")"
harmony_runtime_version="$(sed -n "s/.*versionName: string = '\([^']*\)'.*/\1/p" \
  "${project_dir}/entry/src/main/ets/core/AppBuildInfo.ets")"
android_version_line="$(sed -n '/val current = AppVersion/p' \
  "${repository_dir}/komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt")"
android_version_base="$(printf '%s\n' "${android_version_line}" | \
  sed -nE 's/.*AppVersion\(([0-9]+), ([0-9]+), ([0-9]+).*/\1.\2.\3/p')"
android_pre_release="$(printf '%s\n' "${android_version_line}" | \
  sed -nE 's/.*AppVersion\([^)]*, "([^"]+)"\).*/\1/p')"
android_version="${android_version_base}${android_pre_release:+-${android_pre_release}}"

# AppGallery only accepts HarmonyOS package version names made of digits and
# dots. Keep the public SemVer for runtime/about surfaces, while encoding
# X.Y.Z-beta.N as X.Y.Z.N in AppScope/app.json5.
harmony_package_version="${catalog_version}"
if [[ "${catalog_version}" =~ ^([0-9]+\.[0-9]+\.[0-9]+)-beta\.([0-9]+)$ ]]; then
  harmony_package_version="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}"
fi

if [[ -z "${catalog_version}" || -z "${harmony_manifest_version}" || -z "${harmony_runtime_version}" || \
      -z "${android_version}" ]]; then
  echo "Unable to read every application version source." >&2
  exit 1
fi

if [[ "${harmony_package_version}" != "${harmony_manifest_version}" || \
      "${catalog_version}" != "${harmony_runtime_version}" || \
      "${catalog_version}" != "${android_version}" ]]; then
  echo "Application version mismatch:" >&2
  echo "  version catalog: ${catalog_version}" >&2
  echo "  Android runtime: ${android_version}" >&2
  echo "  Harmony package expected: ${harmony_package_version}" >&2
  echo "  Harmony manifest: ${harmony_manifest_version}" >&2
  echo "  Harmony runtime: ${harmony_runtime_version}" >&2
  exit 1
fi

echo "Application versions aligned: ${catalog_version} (Harmony package ${harmony_package_version})"
