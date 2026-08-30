#!/usr/bin/env bash
set -euo pipefail

NOTES_FILE=""
EXPECTED_VERSION=""
EXPECTED_LEVEL=""
RELEASE_TITLE=""

usage() {
  cat <<'EOF'
Usage: scripts/check-release-notes.sh --file PATH --version X.Y.Z[-PRERELEASE] \
  --title "Komelia vX.Y.Z[-PRERELEASE]" [--level patch|minor|major]

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
    --title)
      RELEASE_TITLE="${2:-}"
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

if [[ ! "$EXPECTED_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-beta\.([1-9][0-9]*))?$ ]]; then
  echo "--version must be X.Y.Z or X.Y.Z-beta.N: ${EXPECTED_VERSION:-missing}" >&2
  exit 2
fi

if [[ -z "$RELEASE_TITLE" ]]; then
  echo "Missing required --title argument." >&2
  exit 2
fi

if [[ "$RELEASE_TITLE" != "Komelia v$EXPECTED_VERSION" ]]; then
  echo "Release title must be exactly: Komelia v$EXPECTED_VERSION" >&2
  exit 1
fi

if [[ ! -f "$NOTES_FILE" ]]; then
  echo "Release notes file not found: $NOTES_FILE" >&2
  exit 1
fi

if grep -Eq '\{\{[^}]+\}\}' "$NOTES_FILE"; then
  echo "Release notes still contain template placeholders: $NOTES_FILE" >&2
  exit 1
fi

if [[ "$(grep -Fxc "## 中文" "$NOTES_FILE")" -ne 1 ]]; then
  echo "Release notes must contain one Chinese section." >&2
  exit 1
fi

if [[ "$(grep -Fxc "## English" "$NOTES_FILE")" -ne 1 ]]; then
  echo "Release notes must contain one English section." >&2
  exit 1
fi

chinese_line="$(grep -Fnx "## 中文" "$NOTES_FILE" | cut -d: -f1)"
english_line="$(grep -Fnx "## English" "$NOTES_FILE" | cut -d: -f1)"
if (( chinese_line >= english_line )); then
  echo "The Chinese section must appear before the English section." >&2
  exit 1
fi

while IFS= read -r heading; do
  case "$heading" in
    "## 中文"|"## English") ;;
    *)
      echo "Release notes contain an unsupported heading: $heading" >&2
      exit 1
      ;;
  esac
done < <(grep -E '^#{1,6} ' "$NOTES_FILE" || true)

if ! awk '
  /^## 中文$/ {
    section = "zh"
    next
  }
  /^## English$/ {
    section = "en"
    next
  }
  /^- / {
    if (section == "zh") chinese_entries++
    else if (section == "en") english_entries++
    else invalid = 1
    next
  }
  /^[[:space:]]*$/ { next }
  { invalid = 1 }
  END {
    exit(invalid || chinese_entries == 0 || english_entries == 0 || chinese_entries != english_entries)
  }
' "$NOTES_FILE"; then
  echo "Release notes must contain matching Chinese and English bullet lists." >&2
  exit 1
fi

for forbidden_text in \
  "版本类型 / Release type:" \
  "完整变更 / Full changelog:" \
  "本项目基于 [Snd-R/Komelia]" \
  "This fork is based on [Snd-R/Komelia]" \
  "SHA-256" \
  "- 中文：" \
  "  English:"; do
  if grep -Fq -- "$forbidden_text" "$NOTES_FILE"; then
    echo "Release notes contain maintainer-only information: $forbidden_text" >&2
    exit 1
  fi
done

if [[ -n "$EXPECTED_LEVEL" ]]; then
  case "$EXPECTED_LEVEL" in
    patch|minor|major) ;;
    *)
      echo "--level must be patch, minor, or major: $EXPECTED_LEVEL" >&2
      exit 2
      ;;
  esac
fi

echo "Release notes check passed: $RELEASE_TITLE"
