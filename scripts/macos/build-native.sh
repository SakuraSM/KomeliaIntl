#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
[[ $(uname -s) == Darwin ]] || { echo 'Run this script on macOS.' >&2; exit 1; }
case $(uname -m) in
  arm64) arch=arm64; ort_version=1.25.0; ort_arch=aarch64; ort_sha=a8c2da0adfd603305e809368716bef3bb6118e840871eb4982f53ba6cfc54e6c ;;
  x86_64) arch=x64; ort_version=1.23.2; ort_arch=x64; ort_sha=e0ab4a1af57d2da09097202f2dfd691e390c82e81183314788ccde4cf7c3cc38 ;;
  *) echo 'Unsupported macOS architecture.' >&2; exit 1 ;;
esac
for command in brew cmake pkg-config meson ninja git patch curl unzip python3 codesign; do
  command -v "$command" >/dev/null || { echo "Missing required tool: $command" >&2; exit 1; }
done
[[ -f third_party/libvips/meson.build && -f third_party/onnxruntime/include/onnxruntime/core/session/onnxruntime_c_api.h ]] || {
  echo 'Initialize the Git submodules before building.' >&2; exit 1;
}
work="$root/build/macos-native-$arch"
output="$root/build/macos-native-resources/macos/$arch"
mkdir -p "$work" "$output"
rm -rf "$work/heif-src" "$work/heif-build" "$work/heif-install" "$work/vips-src" "$work/vips-build" "$work/vips-install" "$work/vips-jni" "$work/pdf-build" "$work/ort-build" "$work/ort" "$output"
mkdir -p "$work/heif-src" "$work/vips-src" "$work/ort/lib" "$work/ort/include/onnxruntime" "$output"
git -C third_party/libheif archive HEAD | tar -xf - -C "$work/heif-src"
cmake -S "$work/heif-src" -B "$work/heif-build" -DCMAKE_INSTALL_PREFIX="$work/heif-install" \
  -DCMAKE_INSTALL_LIBDIR=lib -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON \
  -DWITH_DAV1D=ON -DWITH_LIBDE265=ON -DENABLE_PLUGIN_LOADING=NO \
  -DWITH_DAV1D_PLUGIN=OFF -DWITH_LIBDE265_PLUGIN=OFF -DWITH_GDK_PIXBUF=OFF \
  -DWITH_X264=OFF -DWITH_X265=OFF -DWITH_AOM_DECODER=OFF -DWITH_AOM_ENCODER=OFF -DWITH_SvtEnc=OFF \
  -DWITH_FFMPEG_DECODER=OFF -DWITH_FFMPEG_ENCODER=OFF -DWITH_JPEG_DECODER=OFF \
  -DWITH_JPEG_ENCODER=OFF -DWITH_KVAZAAR=OFF -DWITH_OpenJPEG_DECODER=OFF \
  -DWITH_OpenJPEG_ENCODER=OFF -DWITH_EXAMPLES=OFF -DBUILD_TESTING=OFF
cmake --build "$work/heif-build" --parallel 4
cmake --install "$work/heif-build"
export PKG_CONFIG_PATH="$work/heif-install/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
git -C third_party/libvips archive HEAD | tar -xf - -C "$work/vips-src"
patch -d "$work/vips-src" -p1 < cmake/external/patches/vips_thumbnail_resampling_kernel.patch
meson setup "$work/vips-build" "$work/vips-src" --prefix "$work/vips-install" --libdir lib \
  --buildtype release -Ddefault_library=shared \
  -Dexif=enabled -Dhighway=enabled -Djpeg-xl=enabled -Djpeg=enabled -Dcgif=enabled \
  -Dheif=enabled -Dlcms=enabled -Dpng=enabled -Dtiff=enabled -Dwebp=enabled \
  -Ddeprecated=false -Dexamples=false -Dcplusplus=false -Dmodules=disabled \
  -Dintrospection=disabled -Dvapi=false -Dcfitsio=disabled -Dfftw=disabled \
  -Dfontconfig=disabled -Darchive=disabled -Dheif-module=disabled -Dimagequant=disabled \
  -Djpeg-xl-module=disabled -Dmagick=disabled -Dmatio=disabled -Dnifti=disabled \
  -Dopenexr=disabled -Dopenjpeg=disabled -Dopenslide=disabled -Dorc=disabled \
  -Dpangocairo=disabled -Dpdfium=disabled -Dpoppler=disabled -Dquantizr=disabled \
  -Dspng=disabled -Drsvg=disabled -Draw=disabled -Duhdr=disabled
meson compile -C "$work/vips-build"
meson install -C "$work/vips-build"
export PKG_CONFIG_PATH="$work/vips-install/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
cmake -S komelia-infra/image-decoder/vips/native -B "$work/vips-jni" -DCMAKE_BUILD_TYPE=Release
cmake --build "$work/vips-jni" --parallel 4
cmake -S komelia-domain/offline/native -B "$work/pdf-build" -DCMAKE_BUILD_TYPE=Release
cmake --build "$work/pdf-build" --parallel 4
jar="$work/onnxruntime-$ort_version.jar"
if [[ ! -f "$jar" ]] || [[ $(shasum -a 256 "$jar" | awk '{print $1}') != "$ort_sha" ]]; then
  curl -fL --retry 3 -o "$jar" "https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime/$ort_version/onnxruntime-$ort_version.jar"
fi
[[ $(shasum -a 256 "$jar" | awk '{print $1}') == "$ort_sha" ]] || { echo 'ONNX Runtime checksum mismatch.' >&2; exit 1; }
unzip -p "$jar" "ai/onnxruntime/native/osx-$ort_arch/libonnxruntime.dylib" > "$work/ort/lib/libonnxruntime.dylib"
ln -s libonnxruntime.dylib "$work/ort/lib/libonnxruntime.$ort_version.dylib"
cp third_party/onnxruntime/include/onnxruntime/core/session/*.h "$work/ort/include/onnxruntime/"
omp_prefix=$(brew --prefix libomp)
cmake -S komelia-infra/onnxruntime/native -B "$work/ort-build" -DCMAKE_BUILD_TYPE=Release \
  -DONNXRUNTIME_CUSTOM_PATH="$work/ort" \
  -DKOMELIA_VIPS_INCLUDE="$root/komelia-infra/image-decoder/vips/native/src/vips" \
  -DKOMELIA_VIPS_LIB="$work/vips-jni/libkomelia_vips.dylib" \
  -DOpenMP_C_FLAGS='-Xpreprocessor -fopenmp' -DOpenMP_C_LIB_NAMES=omp \
  -DOpenMP_omp_LIBRARY="$omp_prefix/lib/libomp.dylib" -DOpenMP_C_INCLUDE_DIR="$omp_prefix/include"
cmake --build "$work/ort-build" --parallel 4
python3 scripts/macos/bundle-dylibs.py --output "$output" \
  --search "$work/ort/lib" --search "$work/vips-jni" --search "$work/vips-install/lib" --search "$work/heif-install/lib" \
  "$work/vips-jni/libkomelia_vips.dylib" "$work/pdf-build/libkomelia_pdf.dylib" "$work/ort-build/libkomelia_onnxruntime.dylib"
mkdir -p "$output/licenses/libvips" "$output/licenses/libheif" "$output/licenses/onnxruntime"
cp third_party/libvips/LICENSE "$output/licenses/libvips/"
cp third_party/libheif/COPYING "$output/licenses/libheif/"
cp third_party/onnxruntime/LICENSE "$output/licenses/onnxruntime/"
echo "Native resources are ready for macOS $arch: $output"
