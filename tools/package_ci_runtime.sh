#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-}"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: tools/package_ci_runtime.sh <major.minor.patch>" >&2
  exit 2
fi

NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK_ROOT" ]]; then
  echo "ANDROID_NDK_HOME or ANDROID_NDK_ROOT must point to the Android NDK" >&2
  exit 1
fi

HOST_TAG="linux-x86_64"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/$HOST_TAG/bin"
ARM64_LINKER="$TOOLCHAIN/aarch64-linux-android24-clang"
ARMV7_LINKER="$TOOLCHAIN/armv7a-linux-androideabi24-clang"
test -x "$ARM64_LINKER"
test -x "$ARMV7_LINKER"

export CI_RUNTIME_VERSION="$VERSION"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ARM64_LINKER"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$ARMV7_LINKER"

rustup target add aarch64-linux-android armv7-linux-androideabi
cargo build --manifest-path "$ROOT/runtime/ci-runtime/Cargo.toml" \
  --lib --release --target aarch64-linux-android
cargo build --manifest-path "$ROOT/runtime/ci-runtime/Cargo.toml" \
  --lib --release --target armv7-linux-androideabi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/android/arm64-v8a" "$WORK/android/armeabi-v7a" "$ROOT/dist"

cp "$ROOT/runtime/ci-runtime/target/aarch64-linux-android/release/libci_runtime.so" \
  "$WORK/android/arm64-v8a/libci_runtime.so"
cp "$ROOT/runtime/ci-runtime/target/armv7-linux-androideabi/release/libci_runtime.so" \
  "$WORK/android/armeabi-v7a/libci_runtime.so"

ARM64_SHA="$(sha256sum "$WORK/android/arm64-v8a/libci_runtime.so" | cut -d' ' -f1)"
ARMV7_SHA="$(sha256sum "$WORK/android/armeabi-v7a/libci_runtime.so" | cut -d' ' -f1)"
cat > "$WORK/manifest.json" <<EOF
{
  "name": "CI Runtime",
  "version": "$VERSION",
  "runtime_api_version": 1,
  "room_format_min": 10,
  "room_format_max": 10,
  "sha256": {
    "arm64-v8a": "$ARM64_SHA",
    "armeabi-v7a": "$ARMV7_SHA"
  }
}
EOF

PACKAGE="$ROOT/dist/ci-runtime-$VERSION.ciruntime"
rm -f "$PACKAGE" "$PACKAGE.sha256"
(
  cd "$WORK"
  zip -q "$PACKAGE" manifest.json android/arm64-v8a/libci_runtime.so android/armeabi-v7a/libci_runtime.so
)
sha256sum "$PACKAGE" > "$PACKAGE.sha256"
echo "Created $PACKAGE"
echo "Rust CI Runtime $VERSION; API 1; .room v10"
cat "$PACKAGE.sha256"
