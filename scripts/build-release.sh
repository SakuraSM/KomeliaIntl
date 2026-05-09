#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
APP_BUILD_FILE="$ROOT_DIR/komelia-app/build.gradle.kts"

bump="patch"
target_version=""
target_version_code=""
variant="standalone"
artifact="apk"
skip_build="false"

usage() {
  cat <<'EOF'
Usage: scripts/build-release.sh [options]

Options:
  --bump patch|minor|major   Bump app-version. Default: patch.
  --version X.Y.Z            Set app-version explicitly.
  --version-code N           Set Android versionCode explicitly. Default: current + 1.
  --variant standalone|fdroid|play
                             Android manifest variant. Default: standalone.
  --apk                      Build release APK with assembleRelease. Default.
  --bundle                   Build release AAB with bundleRelease.
  --skip-build               Only update version files.
  -h, --help                 Show this help.

Examples:
  scripts/build-release.sh
  scripts/build-release.sh --bump minor
  scripts/build-release.sh --version 0.19.0 --version-code 20 --bundle --variant play
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bump)
      bump="${2:-}"
      shift 2
      ;;
    --version)
      target_version="${2:-}"
      shift 2
      ;;
    --version-code)
      target_version_code="${2:-}"
      shift 2
      ;;
    --variant)
      variant="${2:-}"
      shift 2
      ;;
    --apk)
      artifact="apk"
      shift
      ;;
    --bundle)
      artifact="bundle"
      shift
      ;;
    --skip-build)
      skip_build="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

case "$bump" in
  patch|minor|major) ;;
  *)
    echo "--bump must be one of: patch, minor, major" >&2
    exit 1
    ;;
esac

case "$variant" in
  standalone|fdroid|play) ;;
  *)
    echo "--variant must be one of: standalone, fdroid, play" >&2
    exit 1
    ;;
esac

current_version="$(sed -nE 's/^app-version = "([^"]+)"/\1/p' "$VERSION_FILE")"
if [[ -z "$current_version" ]]; then
  echo "Could not read app-version from $VERSION_FILE" >&2
  exit 1
fi

current_version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)/\1/p' "$APP_BUILD_FILE" | head -n 1)"
if [[ -z "$current_version_code" ]]; then
  echo "Could not read versionCode from $APP_BUILD_FILE" >&2
  exit 1
fi

if [[ -z "$target_version" ]]; then
  IFS='.' read -r major minor patch <<<"$current_version"
  if [[ ! "$major" =~ ^[0-9]+$ || ! "$minor" =~ ^[0-9]+$ || ! "$patch" =~ ^[0-9]+$ ]]; then
    echo "app-version must be numeric semver X.Y.Z, got: $current_version" >&2
    exit 1
  fi

  case "$bump" in
    patch)
      patch=$((patch + 1))
      ;;
    minor)
      minor=$((minor + 1))
      patch=0
      ;;
    major)
      major=$((major + 1))
      minor=0
      patch=0
      ;;
  esac
  target_version="$major.$minor.$patch"
fi

if [[ ! "$target_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "--version must be numeric semver X.Y.Z, got: $target_version" >&2
  exit 1
fi

if [[ -z "$target_version_code" ]]; then
  target_version_code=$((current_version_code + 1))
fi

if [[ ! "$target_version_code" =~ ^[0-9]+$ ]]; then
  echo "--version-code must be a positive integer, got: $target_version_code" >&2
  exit 1
fi

if [[ "$target_version" == "$current_version" && "$target_version_code" == "$current_version_code" ]]; then
  echo "Version is already $target_version ($target_version_code)"
else
  sed -i.bak -E "s/^app-version = \"[^\"]+\"/app-version = \"$target_version\"/" "$VERSION_FILE"
  sed -i.bak -E "s/^([[:space:]]*)versionCode = [0-9]+/\1versionCode = $target_version_code/" "$APP_BUILD_FILE"
  rm -f "$VERSION_FILE.bak" "$APP_BUILD_FILE.bak"
fi

echo "App version: $current_version ($current_version_code) -> $target_version ($target_version_code)"

if [[ "$skip_build" == "true" ]]; then
  exit 0
fi

gradle_task=":komelia-app:assembleRelease"
if [[ "$artifact" == "bundle" ]]; then
  gradle_task=":komelia-app:bundleRelease"
fi

exec "$ROOT_DIR/gradlew" "-Psnd.android.variant=$variant" -PrequireReleaseSigning=true "$gradle_task"
