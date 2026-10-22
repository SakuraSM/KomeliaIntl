#!/bin/bash
set -e

mkdir -p ./cmake/build/sysroot
cd ./cmake/build

# Compile GLib consumers against the builder's system headers, also used by WebKitGTK.
# New bundled headers can select APIs absent from an older system GLib at runtime.
mkdir -p system-glib-pkgconfig
for module in glib-2.0 gobject-2.0 gio-2.0 gio-unix-2.0 gmodule-2.0 gmodule-export-2.0 gmodule-no-export-2.0 gthread-2.0; do
    system_pc_dir=$(env -u PKG_CONFIG_PATH -u PKG_CONFIG_LIBDIR -u PKG_CONFIG_SYSROOT_DIR \
        pkg-config --variable=pcfiledir "$module")
    test -f "$system_pc_dir/$module.pc"
    ln -sf "$system_pc_dir/$module.pc" "system-glib-pkgconfig/$module.pc"
done
export PKG_CONFIG_PATH="$(readlink -f ./system-glib-pkgconfig):$(readlink -f .)/sysroot/lib/pkgconfig"
export PKG_CONFIG_PATH_CUSTOM="$PKG_CONFIG_PATH"
printf 'Linux GLib compilation baseline: %s\n' "$(pkg-config --modversion glib-2.0)"

cmake ../.. -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DROCM_GPU_ENUMERATION=ON \
        -DCUDA_GPU_ENUMERATION=ON \
        -DVULKAN_GPU_ENUMERATION=ON \
        -DDXGI_GPU_ENUMERATION=OFF \
        -DCUDA_CUSTOM_PATH="$CUDA_CUSTOM_PATH" \
        -DROCM_CUSTOM_PATH="$ROCM_CUSTOM_PATH" \
        -DWEBVIEW_USE_COMPAT_MINGW=OFF

cmake --build . -j $(nproc)

bash ../check-linux-glib-abi.sh "$PWD/sysroot/lib" "$PWD/glib-abi"

for lib in sysroot/lib/*so; do
    strip $lib
done
