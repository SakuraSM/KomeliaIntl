#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$ROOT_DIR/scripts/check-semver-bump.sh"

expect_pass() {
  "$CHECK" --previous "$1" --next "$2" --level "$3" >/dev/null
}

expect_fail() {
  if "$CHECK" --previous "$1" --next "$2" --level "$3" >/dev/null 2>&1; then
    echo "Expected version check to fail: $1 -> $2 ($3)" >&2
    exit 1
  fi
}

expect_pass 0.18.16 0.18.17 patch
expect_pass 0.18.16 0.19.0 minor
expect_pass 0.18.16 1.0.0 major
expect_pass 1.4.9 1.4.10 patch
expect_pass 1.4.9 1.5.0 minor
expect_pass 1.4.9 2.0.0 major

expect_fail 0.18.16 0.19.0 patch
expect_fail 0.18.16 0.18.17 minor
expect_fail 0.18.16 1.0.1 major
expect_fail 1.4.9 1.4.9 patch
expect_fail 01.4.9 1.4.10 patch

echo "Release policy tests passed."
