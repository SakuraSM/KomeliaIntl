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

expect_fail 0.18.16 0.19.0 patch
expect_fail 0.18.16 0.18.17 minor
expect_fail 0.18.16 1.0.1 major
expect_fail 1.4.9 1.4.9 patch
expect_fail 01.4.9 1.4.10 patch

cat > "$TEST_DIR/valid-notes.md" <<'EOF'
## Komelia Intl v0.18.17

### 变更 / Changes

- 中文：修复阅读器返回和离线缓存管理问题。
  English: Fixed reader back navigation and offline cache management.
EOF

"$NOTES_CHECK" \
  --file "$TEST_DIR/valid-notes.md" \
  --version 0.18.17 \
  --level patch >/dev/null

expect_notes_fail() {
  if "$NOTES_CHECK" --file "$1" --version 0.18.17 --level patch >/dev/null 2>&1; then
    echo "Expected release notes check to fail: $1" >&2
    exit 1
  fi
}

cat > "$TEST_DIR/unpaired-notes.md" <<'EOF'
## Komelia Intl v0.18.17

### 变更 / Changes

- 中文：修复阅读器返回问题。
EOF
expect_notes_fail "$TEST_DIR/unpaired-notes.md"

cat > "$TEST_DIR/misaligned-notes.md" <<'EOF'
## Komelia Intl v0.18.17

### 变更 / Changes

- 中文：修复阅读器返回问题。
- 中文：增加离线缓存管理。
  English: Added offline cache management.
EOF
expect_notes_fail "$TEST_DIR/misaligned-notes.md"

cat > "$TEST_DIR/verbose-notes.md" <<'EOF'
## Komelia Intl v0.18.17

### 变更 / Changes

- 中文：修复阅读器返回问题。
  English: Fixed reader back navigation.

### 验证 / Verification

- Ran internal build commands.
EOF
expect_notes_fail "$TEST_DIR/verbose-notes.md"

cat > "$TEST_DIR/extra-section-notes.md" <<'EOF'
## Komelia Intl v0.18.17

### 变更 / Changes

- 中文：修复阅读器返回问题。
  English: Fixed reader back navigation.

## 下载 / Downloads

- Android APK
EOF
expect_notes_fail "$TEST_DIR/extra-section-notes.md"

cat > "$TEST_DIR/placeholder-notes.md" <<'EOF'
## Komelia Intl v{{VERSION}}

### 变更 / Changes

- 中文：{{CHANGE_ZH}}
  English: {{CHANGE_EN}}
EOF
expect_notes_fail "$TEST_DIR/placeholder-notes.md"

echo "Release policy tests passed."
