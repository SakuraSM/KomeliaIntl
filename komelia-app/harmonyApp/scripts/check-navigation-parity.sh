#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
pages_dir="${project_dir}/entry/src/main/ets/pages"
back_icon="${project_dir}/entry/src/main/resources/base/media/navigation_back.svg"
primary_navigation="${project_dir}/entry/src/main/ets/components/AppPrimaryNavigation.ets"
failed=0

if [[ ! -f "${back_icon}" ]]; then
  echo "HarmonyOS navigation back icon is missing: ${back_icon}" >&2
  failed=1
fi

if grep -R -n -E "Button\('‹'\)|Text\('‹'\)\.fontSize\(34\)" "${pages_dir}"; then
  echo "Text glyphs must not be used for top-level back navigation." >&2
  failed=1
fi

if ! grep -Fq 'primaryNavigationDecision(this.selectedDestination, destination, this.nested)' \
  "${primary_navigation}"; then
  echo "Primary navigation must ignore repeat taps on the current root destination." >&2
  failed=1
fi

while IFS= read -r page; do
  if ! grep -q "accessibilityText(\$r('app.string.back'))" "${page}"; then
    echo "Back navigation is missing an accessibility label: $(basename "${page}")" >&2
    failed=1
  fi
done < <(grep -R -l "app.media.navigation_back" "${pages_dir}" | sort)

# Android keeps bottom navigation / NavigationRail around every ordinary
# destination stack. Only the root host and immersive reader flow replace it.
immersive_pages="Index.ets ImageReader.ets EpubReader.ets ColorCorrection.ets"
while IFS= read -r page; do
  page_name="$(basename "${page}")"
  if [[ " ${immersive_pages} " == *" ${page_name} "* ]]; then
    if grep -q "AppNavigationFrame" "${page}"; then
      echo "Immersive page must not render primary navigation: ${page_name}" >&2
      failed=1
    fi
    continue
  fi
  if ! grep -q "AppNavigationFrame" "${page}"; then
    echo "Ordinary route is missing persistent primary navigation: ${page_name}" >&2
    failed=1
  fi
done < <(grep -R -l '^@Entry' "${pages_dir}" | sort)

if [[ "${failed}" -ne 0 ]]; then
  exit 1
fi

echo "HarmonyOS navigation icon parity check passed."
