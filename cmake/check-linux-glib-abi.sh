#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

native_directory="${1:?Pass the native library directory}"
evidence_directory="${2:?Pass the ABI evidence directory}"
mkdir -p "$evidence_directory"

system_libraries=()
for module in glib-2.0 gobject-2.0 gio-2.0 gmodule-2.0; do
    system_libraries+=("$(pkg-config --variable=libdir "$module")/lib$module.so")
done
consumer_libraries=("$native_directory/libvips.so" "$native_directory"/libkomelia_*.so)

nm -D --undefined-only "${consumer_libraries[@]}" \
    | awk '{print $NF}' | sed 's/@.*//' | grep '^g_' | sort -u \
    > "$evidence_directory/required.txt"
nm -D --defined-only "${system_libraries[@]}" \
    | awk '{print $NF}' | sed 's/@.*//' | grep '^g_' | sort -u \
    > "$evidence_directory/available.txt"
comm -23 "$evidence_directory/required.txt" "$evidence_directory/available.txt" \
    > "$evidence_directory/missing.txt"

if [[ -s "$evidence_directory/missing.txt" ]]; then
    echo 'Native libraries require GLib symbols outside the system compilation baseline:' >&2
    cat "$evidence_directory/missing.txt" >&2
    exit 1
fi
printf 'Linux GLib ABI verified against %s\n' "$(pkg-config --modversion glib-2.0)"
