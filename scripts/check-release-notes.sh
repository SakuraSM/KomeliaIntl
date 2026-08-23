#!/usr/bin/env bash
set -euo pipefail

NOTES_FILE=""
EXPECTED_VERSION=""
EXPECTED_LEVEL=""

usage() {
  cat <<'EOF'
Usage: scripts/check-release-notes.sh --file PATH [--version X.Y.Z] [--level patch|minor|major]

Validate release notes against .github/RELEASE_TEMPLATE.md.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      NOTES_FILE="${2:-}"
      shift 2
      ;;
    --version)
      EXPECTED_VERSION="${2:-}"
      shift 2
      ;;
    --level)
      EXPECTED_LEVEL="${2:-}"
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

if [[ -z "$NOTES_FILE" ]]; then
  echo "Missing required --file argument." >&2
  exit 2
fi

if [[ ! -f "$NOTES_FILE" ]]; then
  echo "Release notes file not found: $NOTES_FILE" >&2
  exit 1
fi

if rg -q '\{\{[^}]+\}\}' "$NOTES_FILE"; then
  echo "Release notes still contain template placeholders: $NOTES_FILE" >&2
  exit 1
fi

required_headings=(
  "### 修复 / Fixed"
  "### 优化 / Improved"
  "### 验证 / Verification"
  "### 下载 / Downloads"
)

for heading in "${required_headings[@]}"; do
  if ! rg -Fxq "$heading" "$NOTES_FILE"; then
    echo "Release notes are missing required heading: $heading" >&2
    exit 1
  fi
done

if ! rg -q '^关联 Issue / Related issues: ' "$NOTES_FILE"; then
  echo "Release notes are missing the bilingual Related issues line." >&2
  exit 1
fi

if ! rg -q '^完整变更 / Full changelog: https://github.com/SakuraSM/KomeliaIntl/compare/' "$NOTES_FILE"; then
  echo "Release notes are missing the bilingual full changelog comparison URL." >&2
  exit 1
fi

if ! rg -Fq '本项目基于 [Snd-R/Komelia](https://github.com/Snd-R/Komelia)' "$NOTES_FILE"; then
  echo "Release notes are missing the Chinese upstream attribution." >&2
  exit 1
fi

if ! rg -Fq 'This fork is based on [Snd-R/Komelia](https://github.com/Snd-R/Komelia)' "$NOTES_FILE"; then
  echo "Release notes are missing the English upstream attribution." >&2
  exit 1
fi

if ! rg -q '^- 中文：.+' "$NOTES_FILE" || ! rg -q '^  English: .+' "$NOTES_FILE"; then
  echo "Release notes must include paired Chinese and English entries." >&2
  exit 1
fi

if [[ -n "$EXPECTED_VERSION" ]] && ! rg -Fxq "## Komelia Intl v$EXPECTED_VERSION" "$NOTES_FILE"; then
  echo "Release notes version mismatch: expected v$EXPECTED_VERSION" >&2
  exit 1
fi

if [[ -n "$EXPECTED_LEVEL" ]]; then
  case "$EXPECTED_LEVEL" in
    patch)
      expected_release_type="版本类型 / Release type: 补丁 / Patch"
      ;;
    minor)
      expected_release_type="版本类型 / Release type: 次版本 / Minor"
      ;;
    major)
      expected_release_type="版本类型 / Release type: 主版本 / Major"
      ;;
    *)
      echo "--level must be patch, minor, or major: $EXPECTED_LEVEL" >&2
      exit 2
      ;;
  esac

  if ! rg -Fxq "$expected_release_type" "$NOTES_FILE"; then
    echo "Release notes type mismatch: expected $expected_release_type" >&2
    exit 1
  fi
elif ! rg -q '^版本类型 / Release type: (补丁 / Patch|次版本 / Minor|主版本 / Major)$' "$NOTES_FILE"; then
  echo "Release notes are missing a valid bilingual release type." >&2
  exit 1
fi

echo "Release notes check passed: $NOTES_FILE"
