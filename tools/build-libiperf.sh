#!/bin/bash
set -euo pipefail
repo_root=$(cd "$(dirname "$0")/.." && pwd)
platform=${1:?android or ios}
build_root="$repo_root/.native-build/$platform"
mkdir -p "$build_root"
if [[ "$platform" == android ]]; then
  ndk_root=${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/28.0.12433566}
  compiler_root="$ndk_root/toolchains/llvm/prebuilt/darwin-x86_64/bin"
  export CC="$compiler_root/aarch64-linux-android35-clang" AR="$compiler_root/llvm-ar" RANLIB="$compiler_root/llvm-ranlib"
  export CFLAGS="-O2 -fPIC -D_GNU_SOURCE"
  host=aarch64-linux-android
else
  sdk_path=$(xcrun --sdk iphoneos --show-sdk-path)
  export CC="$(xcrun --sdk iphoneos -f clang) -arch arm64 -isysroot $sdk_path -miphoneos-version-min=26.0"
  export AR=$(xcrun -f ar) RANLIB=$(xcrun -f ranlib)
  export CFLAGS="-O2 -fPIC"
  export ac_cv_header_endian_h=no
  host=aarch64-apple-darwin
fi
export CFLAGS="$CFLAGS -I$repo_root/native/iperf"
cd "$build_root"
if [[ ! -f Makefile ]]; then
  "$repo_root/third_party/iperf3/configure" --host="$host" --without-openssl --disable-shared --enable-static --disable-profiling > configure-output.log 2>&1
fi
make -j8 -C src libiperf.la > build-output.log 2>&1
