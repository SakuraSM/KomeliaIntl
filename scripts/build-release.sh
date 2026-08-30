#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
APP_BUILD_FILE="$ROOT_DIR/komelia-app/androidApp/build.gradle.kts"
APP_VERSION_FILE="$ROOT_DIR/komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt"
APP_VERSION_TEST_FILE="$ROOT_DIR/komelia-ui/src/commonTest/kotlin/snd/komelia/ui/AppVersionTest.kt"

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
  --version X.Y.Z[-PRERELEASE]
                             Set app-version explicitly.
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
  scripts/build-release.sh --bump minor --version 0.19.0-beta.1 --version-code 20 --bundle --variant play
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

release_semver_pattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-beta\.([1-9][0-9]*))?$'
if [[ ! "$current_version" =~ $release_semver_pattern ]]; then
  echo "app-version must be X.Y.Z or X.Y.Z-beta.N, got: $current_version" >&2
  exit 1
fi

current_core="${current_version%%-*}"
if [[ -z "$target_version" ]]; then
  IFS='.' read -r major minor patch <<<"$current_core"
  if [[ ! "$major" =~ ^[0-9]+$ || ! "$minor" =~ ^[0-9]+$ || ! "$patch" =~ ^[0-9]+$ ]]; then
    echo "app-version must contain a numeric SemVer core X.Y.Z, got: $current_version" >&2
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

if [[ ! "$target_version" =~ $release_semver_pattern ]]; then
  echo "--version must be X.Y.Z or X.Y.Z-beta.N, got: $target_version" >&2
  exit 1
fi
if [[ "$target_version" == *-beta.* ]]; then
  beta_number="${target_version##*.}"
  if (( beta_number > 998 )); then
    echo "Beta number must be between 1 and 998 for native installer ordering: $beta_number" >&2
    exit 1
  fi
fi

target_core="${target_version%%-*}"
target_pre_release=""
if [[ "$target_version" == *-* ]]; then
  target_pre_release="${target_version#*-}"
fi
IFS='.' read -r target_major target_minor target_patch <<<"$target_core"

if [[ "$current_version" == *-* && "$target_core" == "$current_core" ]]; then
  if [[ "$target_version" == "$current_version" ]]; then
    echo "Target version is already current: $target_version" >&2
    exit 1
  fi
else
  "$ROOT_DIR/scripts/check-semver-bump.sh" \
    --previous "$current_core" \
    --next "$target_version" \
    --level "$bump"
fi

if [[ -z "$target_version_code" ]]; then
  target_version_code=$((current_version_code + 1))
fi

if [[ ! "$target_version_code" =~ ^[1-9][0-9]*$ ]]; then
  echo "--version-code must be a positive integer, got: $target_version_code" >&2
  exit 1
fi

if [[ "$target_version" == "$current_version" && "$target_version_code" == "$current_version_code" ]]; then
  echo "Version is already $target_version ($target_version_code)"
else
  sed -i.bak -E "s/^app-version = \"[^\"]+\"/app-version = \"$target_version\"/" "$VERSION_FILE"
  sed -i.bak -E "s/^([[:space:]]*)versionCode = [0-9]+/\1versionCode = $target_version_code/" "$APP_BUILD_FILE"
fi

runtime_version="AppVersion($target_major, $target_minor, $target_patch)"
if [[ -n "$target_pre_release" ]]; then
  runtime_version="AppVersion($target_major, $target_minor, $target_patch, \"$target_pre_release\")"
fi
sed -i.bak -E \
  "s/val current = AppVersion\([0-9]+, [0-9]+, [0-9]+(, \"[^\"]+\")?\)/val current = $runtime_version/" \
  "$APP_VERSION_FILE"
sed -i.bak -E \
  "s/assertEquals\(\"[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?\", AppVersion.current.toString\(\)\)/assertEquals(\"$target_version\", AppVersion.current.toString())/" \
  "$APP_VERSION_TEST_FILE"
rm -f \
  "$VERSION_FILE.bak" \
  "$APP_BUILD_FILE.bak" \
  "$APP_VERSION_FILE.bak" \
  "$APP_VERSION_TEST_FILE.bak"

echo "App version: $current_version ($current_version_code) -> $target_version ($target_version_code)"

"$ROOT_DIR/scripts/check-release-version.sh" --version "$target_version"

if [[ "$skip_build" == "true" ]]; then
  exit 0
fi

gradle_task=":komelia-app:androidApp:assembleRelease"
if [[ "$artifact" == "bundle" ]]; then
  gradle_task=":komelia-app:androidApp:bundleRelease"
fi

exec "$ROOT_DIR/gradlew" "-Psnd.android.variant=$variant" -PrequireReleaseSigning=true "$gradle_task"
