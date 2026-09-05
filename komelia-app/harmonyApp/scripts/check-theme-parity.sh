#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
base_colors="${project_dir}/entry/src/main/resources/base/element/color.json"
dark_colors="${project_dir}/entry/src/main/resources/dark/element/color.json"
source_root="${project_dir}/entry/src/main/ets"

assert_color() {
  local file="$1"
  local name="$2"
  local value="$3"
  if ! grep -Fq "\"name\": \"${name}\", \"value\": \"${value}\"" "${file}"; then
    echo "HarmonyOS theme token ${name} does not match Android value ${value}: ${file}" >&2
    exit 1
  fi
}

assert_color "${base_colors}" surface '#FCFCFF'
assert_color "${base_colors}" surface_container '#EFEEF8'
assert_color "${base_colors}" surface_high '#FFFFFF'
assert_color "${base_colors}" primary '#5F5D8E'
assert_color "${base_colors}" primary_container '#E5E3F5'
assert_color "${base_colors}" on_surface '#1B1C20'
assert_color "${base_colors}" on_surface_variant '#555964'
assert_color "${base_colors}" outline '#D9D7E4'

assert_color "${dark_colors}" surface '#15151A'
assert_color "${dark_colors}" surface_container '#1D1C24'
assert_color "${dark_colors}" surface_high '#0B0C0E'
assert_color "${dark_colors}" primary '#C5C2F0'
assert_color "${dark_colors}" primary_container '#454269'
assert_color "${dark_colors}" on_surface '#F0F0F3'
assert_color "${dark_colors}" on_surface_variant '#C5C6CD'
assert_color "${dark_colors}" outline '#34363E'

if grep -R -E -n "app\.color\.(surface|surface_container|surface_high)" \
  "${source_root}/pages" "${source_root}/components"; then
  echo 'Surface colors must use KomeliaColors so OLED remains distinct from Dark.' >&2
  exit 1
fi

echo "HarmonyOS theme parity check passed."
