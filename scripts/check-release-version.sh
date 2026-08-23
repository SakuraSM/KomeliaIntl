#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
ANDROID_BUILD_FILE="$ROOT_DIR/komelia-app/androidApp/build.gradle.kts"
APP_VERSION_FILE="$ROOT_DIR/komelia-domain/core/src/commonMain/kotlin/snd/komelia/updates/AppVersion.kt"
APP_VERSION_TEST_FILE="$ROOT_DIR/komelia-ui/src/commonTest/kotlin/snd/komelia/ui/AppVersionTest.kt"

expected_version=""
release_tag=""

usage() {
  cat <<'EOF'
Usage: scripts/check-release-version.sh [options]

Options:
  --version X.Y.Z  Require this application version.
  --tag vX.Y.Z     Require this release tag to match the application version.
  -h, --help       Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      expected_version="${2:-}"
      shift 2
      ;;
    --tag)
      release_tag="${2:-}"
      shift 2
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

catalog_version="$(sed -nE 's/^app-version = "([^"]+)"/\1/p' "$VERSION_FILE")"
runtime_version="$(sed -nE 's/^[[:space:]]*val current = AppVersion\(([0-9]+), ([0-9]+), ([0-9]+)\)/\1.\2.\3/p' "$APP_VERSION_FILE")"
test_version="$(sed -nE 's/^[[:space:]]*assertEquals\("([0-9]+\.[0-9]+\.[0-9]+)", AppVersion.current.toString\(\)\)/\1/p' "$APP_VERSION_TEST_FILE")"
version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+)/\1/p' "$ANDROID_BUILD_FILE" | head -n 1)"

if [[ ! "$catalog_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid app-version in $VERSION_FILE: ${catalog_version:-missing}" >&2
  exit 1
fi

if [[ "$runtime_version" != "$catalog_version" ]]; then
  echo "Runtime version mismatch: AppVersion.current=$runtime_version, app-version=$catalog_version" >&2
  exit 1
fi

if [[ "$test_version" != "$catalog_version" ]]; then
  echo "Version test mismatch: expected=$test_version, app-version=$catalog_version" >&2
  exit 1
fi

if ! grep -Fq 'versionName = libs.versions.app.version.get()' "$ANDROID_BUILD_FILE"; then
  echo "Android versionName must come from libs.versions.app.version" >&2
  exit 1
fi

if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
  echo "Android versionCode must be a positive integer, got: ${version_code:-missing}" >&2
  exit 1
fi

if [[ -n "$expected_version" && "$expected_version" != "$catalog_version" ]]; then
  echo "Expected version $expected_version, got $catalog_version" >&2
  exit 1
fi

if [[ -n "$release_tag" && "$release_tag" != "v$catalog_version" ]]; then
  echo "Release tag mismatch: tag=$release_tag, expected=v$catalog_version" >&2
  exit 1
fi

echo "Release version check passed: v$catalog_version (Android versionCode $version_code)"
