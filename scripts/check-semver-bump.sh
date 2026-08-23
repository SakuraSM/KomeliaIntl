#!/usr/bin/env bash
set -euo pipefail

previous_version=""
next_version=""
release_level=""

usage() {
  cat <<'EOF'
Usage: scripts/check-semver-bump.sh --previous X.Y.Z --next X.Y.Z --level patch|minor|major

Require the next version to be the exact Semantic Versioning increment for the selected release level.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --previous)
      previous_version="${2:-}"
      shift 2
      ;;
    --next)
      next_version="${2:-}"
      shift 2
      ;;
    --level)
      release_level="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

semver_pattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
if [[ ! "$previous_version" =~ $semver_pattern ]]; then
  echo "--previous must be a stable Semantic Versioning value X.Y.Z: ${previous_version:-missing}" >&2
  exit 2
fi
if [[ ! "$next_version" =~ $semver_pattern ]]; then
  echo "--next must be a stable Semantic Versioning value X.Y.Z: ${next_version:-missing}" >&2
  exit 2
fi
if [[ "$release_level" != "patch" && "$release_level" != "minor" && "$release_level" != "major" ]]; then
  echo "--level must be patch, minor, or major: ${release_level:-missing}" >&2
  exit 2
fi

IFS='.' read -r previous_major previous_minor previous_patch <<<"$previous_version"
case "$release_level" in
  patch)
    expected_version="$previous_major.$previous_minor.$((previous_patch + 1))"
    ;;
  minor)
    expected_version="$previous_major.$((previous_minor + 1)).0"
    ;;
  major)
    expected_version="$((previous_major + 1)).0.0"
    ;;
esac

if [[ "$next_version" != "$expected_version" ]]; then
  echo "Invalid $release_level release: $previous_version -> $next_version; expected $expected_version" >&2
  exit 1
fi

echo "Semantic version check passed: $previous_version -> $next_version ($release_level)"
