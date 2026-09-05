#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
pages_dir="${project_dir}/entry/src/main/ets/pages"
failed=0

while IFS= read -r page; do
  name="$(basename "${page}")"
  case "${name}" in
    EpubReader.ets|ImageReader.ets)
      # Immersive readers deliberately own the full safe area.
      continue
      ;;
  esac

  if ! tail -n 36 "${page}" | grep -Eq \
    '\.align\(Alignment\.Top|alignContent: Alignment\.Top|\.justifyContent\(FlexAlign\.Start'; then
    echo "Page root is not explicitly top-aligned: ${name}" >&2
    failed=1
  fi
done < <(find "${pages_dir}" -maxdepth 1 -type f -name '*.ets' | sort)

if [[ "${failed}" -ne 0 ]]; then
  exit 1
fi

echo "HarmonyOS page top-alignment check passed."
