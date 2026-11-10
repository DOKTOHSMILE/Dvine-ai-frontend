#!/bin/sh
# Builds libdvpty.so for arm64 Android with plain clang and lld (no NDK needed: the file uses no C library).
set -e
here=$(cd "$(dirname "$0")" && pwd)
out="$here/../../jniLibs/arm64-v8a/libdvpty.so"
clang --target=aarch64-linux-android34 -Os -fPIC -ffreestanding -fno-stack-protector -nostdlib -shared -fuse-ld=lld \
    -Wl,--hash-style=both -Wl,-z,max-page-size=16384 -Wl,-soname,libdvpty.so -Wl,--build-id=none \
    -o "$out" "$here/dvpty.c"
echo "built $out"
