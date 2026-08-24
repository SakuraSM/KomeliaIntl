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

if [[ "$(rg -Fxc "### 变更 / Changes" "$NOTES_FILE")" -ne 1 ]]; then
  echo "Release notes must contain one Changes heading." >&2
  exit 1
fi

if ! awk '
  /^- 中文：/ {
    chinese_entries++
  }
  /^  English:/ {
    if (previous_line !~ /^- 中文：/) {
      invalid = 1
    }
    english_entries++
  }
  {
    previous_line = $0
  }
  END {
    exit(invalid || chinese_entries == 0 || chinese_entries != english_entries)
  }
' "$NOTES_FILE"; then
  echo "Release notes must include paired Chinese and English entries." >&2
  exit 1
fi

while IFS= read -r heading; do
  if [[ "$heading" == "### 变更 / Changes" ]]; then
    continue
  fi
  if [[ "$heading" =~ ^##\ Komelia\ Intl\ v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    continue
  fi
  if [[ -n "$heading" ]]; then
    echo "Release notes contain an unsupported section: $heading" >&2
    exit 1
  fi
done < <(rg '^#{1,6} ' "$NOTES_FILE" || true)

if ! rg -q '^## Komelia Intl v[0-9]+\.[0-9]+\.[0-9]+$' "$NOTES_FILE"; then
  echo "Release notes are missing a semantic version title." >&2
  exit 1
fi

for forbidden_text in \
  "版本类型 / Release type:" \
  "完整变更 / Full changelog:" \
  "本项目基于 [Snd-R/Komelia]" \
  "This fork is based on [Snd-R/Komelia]"; do
  if rg -Fq "$forbidden_text" "$NOTES_FILE"; then
    echo "Release notes contain maintainer-only information: $forbidden_text" >&2
    exit 1
  fi
done

if [[ -n "$EXPECTED_VERSION" ]] && ! rg -Fxq "## Komelia Intl v$EXPECTED_VERSION" "$NOTES_FILE"; then
  echo "Release notes version mismatch: expected v$EXPECTED_VERSION" >&2
  exit 1
fi

if [[ -n "$EXPECTED_LEVEL" ]]; then
  case "$EXPECTED_LEVEL" in
    patch|minor|major) ;;
    *)
      echo "--level must be patch, minor, or major: $EXPECTED_LEVEL" >&2
      exit 2
      ;;
  esac
fi

echo "Release notes check passed: $NOTES_FILE"
