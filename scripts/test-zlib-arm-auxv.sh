#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/komelia-zlib-auxv.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT
mkdir -p "$work_dir/include/sys" "$work_dir/source/arch/arm"
cp "$root/third_party/zlib-ng/arch/arm/arm_features.c" "$work_dir/source/arch/arm/arm_features.c"
patch -s -d "$work_dir/source" -p1 < "$root/cmake/external/patches/zlib-ng-arm-platform-null.patch"
cat > "$work_dir/include/sys/auxv.h" <<'HEADER'
#pragma once
#define AT_PLATFORM 15
#define AT_HWCAP 16
unsigned long getauxval(unsigned long key);
HEADER
cat > "$work_dir/probe.c" <<'SOURCE'
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "zbuild.h"
#undef __aarch64__
#undef __APPLE__
#define __linux__ 1
#define HAVE_SYS_AUXV_H 1
#define getauxval fake_getauxval
#include "arch/arm/arm_features.c"
static const char *test_platform;
unsigned long fake_getauxval(unsigned long key) {
    return key == AT_PLATFORM ? (unsigned long)test_platform : 0;
}
int main(void) {
    const struct { const char *name; int expected; } cases[] = {
        {NULL, 0}, {"v6l", 1}, {"v7l", 1}, {"v8l", 1}, {"x86", 0}, {"", 0}
    };
    for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++) {
        struct arm_cpu_features features = {0};
        test_platform = cases[i].name;
        arm_check_features(&features);
        if (features.has_simd != cases[i].expected) return 1;
    }
    puts("zlib ARM platform detection: null and platform controls passed");
    return 0;
}
SOURCE
cc -std=c11 -Wall -Wextra -Werror -I "$work_dir/include" -I "$work_dir/source" \
    -I "$root/third_party/zlib-ng" -I "$root/third_party/zlib-ng/arch/arm" \
    "$work_dir/probe.c" -o "$work_dir/probe"
"$work_dir/probe"
