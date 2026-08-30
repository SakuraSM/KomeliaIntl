#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK="$ROOT_DIR/scripts/check-semver-bump.sh"
NOTES_CHECK="$ROOT_DIR/scripts/check-release-notes.sh"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

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
expect_pass 0.19.4 0.20.0-beta.1 minor
expect_pass 1.4.9 1.5.0-beta.2 minor

expect_fail 0.18.16 0.19.0 patch
expect_fail 0.18.16 0.18.17 minor
expect_fail 0.18.16 1.0.1 major
expect_fail 1.4.9 1.4.9 patch
expect_fail 01.4.9 1.4.10 patch
expect_fail 0.19.4 0.20.1-beta.1 minor
expect_fail 0.19.4 0.20.0-beta.01 minor
expect_fail 0.19.4 0.20.0-rc.1 minor
expect_fail 0.19.4 0.20.0-beta.999 minor

cat > "$TEST_DIR/valid-notes.md" <<'EOF'
## 中文

- 修复阅读器返回问题。
- 增加离线缓存管理。

## English

- Fixed reader back navigation.
- Added offline cache management.
EOF

env PATH="/usr/bin:/bin" "$NOTES_CHECK" \
  --file "$TEST_DIR/valid-notes.md" \
  --version 0.18.17 \
  --title "Komelia v0.18.17" \
  --level patch >/dev/null

env PATH="/usr/bin:/bin" "$NOTES_CHECK" \
  --file "$TEST_DIR/valid-notes.md" \
  --version 0.20.0-beta.1 \
  --title "Komelia v0.20.0-beta.1" \
  --level minor >/dev/null

expect_notes_fail() {
  local title="${2:-Komelia v0.18.17}"
  if "$NOTES_CHECK" --file "$1" --version 0.18.17 --title "$title" --level patch >/dev/null 2>&1; then
    echo "Expected release notes check to fail: $1" >&2
    exit 1
  fi
}

expect_notes_fail "$TEST_DIR/valid-notes.md" "v0.18.17"

cat > "$TEST_DIR/missing-language-notes.md" <<'EOF'
## 中文

- 修复阅读器返回问题。
EOF
expect_notes_fail "$TEST_DIR/missing-language-notes.md"

cat > "$TEST_DIR/wrong-language-order.md" <<'EOF'
## English

- Fixed reader back navigation.

## 中文

- 修复阅读器返回问题。
EOF
expect_notes_fail "$TEST_DIR/wrong-language-order.md"

cat > "$TEST_DIR/mismatched-lists.md" <<'EOF'
## 中文

- 修复阅读器返回问题。
- 增加离线缓存管理。

## English

- Fixed reader back navigation.
EOF
expect_notes_fail "$TEST_DIR/mismatched-lists.md"

cat > "$TEST_DIR/legacy-interleaved-notes.md" <<'EOF'
## 中文

- 中文：修复阅读器返回问题。
  English: Fixed reader back navigation.

## English

- Fixed reader back navigation.
EOF
expect_notes_fail "$TEST_DIR/legacy-interleaved-notes.md"

cat > "$TEST_DIR/extra-section-notes.md" <<'EOF'
## 中文

- 修复阅读器返回问题。

## English

- Fixed reader back navigation.

## 下载 / Downloads

- Android APK
EOF
expect_notes_fail "$TEST_DIR/extra-section-notes.md"

cat > "$TEST_DIR/placeholder-notes.md" <<'EOF'
## 中文

- {{CHANGE_ZH}}

## English

- {{CHANGE_EN}}
EOF
expect_notes_fail "$TEST_DIR/placeholder-notes.md"

echo "Release policy tests passed."
