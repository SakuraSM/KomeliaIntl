#!/usr/bin/env bash
set -euo pipefail

abi=${1:?Android ABI required}
output=${2:?Output path required}
case "$abi" in
  armeabi-v7a) target=armv7a-linux-androideabi26; page_size=4096 ;;
  arm64-v8a) target=aarch64-linux-android26; page_size=16384 ;;
  *) echo "Unsupported ABI: $abi" >&2; exit 1 ;;
esac
case "$(uname -s)" in
  Darwin) host=darwin-x86_64 ;;
  Linux) host=linux-x86_64 ;;
  *) echo "Unsupported host" >&2; exit 1 ;;
esac
root=$(cd "$(dirname "$0")/.." && pwd)
include_dir=$(mktemp -d)
trap 'rm -rf "$include_dir"' EXIT
cp "$root/third_party/libpng/scripts/pnglibconf.h.prebuilt" "$include_dir/pnglibconf.h"
libs="$root/komelia-infra/jni/src/androidMain/jniLibs/$abi"
"${ANDROID_NDK_HOME:?ANDROID_NDK_HOME required}/toolchains/llvm/prebuilt/$host/bin/$target-clang" \
  -shared -fPIC -Wall -Wextra -Werror -I "$include_dir" -I "$root/third_party/libpng" \
  "$root/scripts/android-png-direct-probe.c" "$libs/libpng.so" \
  -Wl,-rpath-link,"$libs" -Wl,-z,max-page-size="$page_size" -Wl,--no-undefined -o "$output"
