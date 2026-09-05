#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
image_component="${project_dir}/entry/src/main/ets/components/AuthenticatedImage.ets"

if ! grep -Fq '.syncLoad(true)' "${image_component}"; then
  echo "Authenticated images must use synchronous PixelMap presentation to avoid one-frame cover flicker." >&2
  exit 1
fi

disappear_body="$(sed -n '/aboutToDisappear(): void {/,/^  }/p' "${image_component}")"
if grep -Fq 'this.source =' <<<"${disappear_body}"; then
  echo "Do not mutate the image @State source from aboutToDisappear; ArkUI can render the placeholder before removal." >&2
  exit 1
fi

if ! grep -Fq 'this.releaseOwnedImage();' <<<"${disappear_body}"; then
  echo "Authenticated images must still release their retained PixelMap when disappearing." >&2
  exit 1
fi

echo "HarmonyOS authenticated image continuity checks passed."
