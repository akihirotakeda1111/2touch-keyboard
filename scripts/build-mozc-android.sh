#!/usr/bin/env bash
# Build Mozc native library and dictionary for Android, then copy into mozc-engine module.
#
# Requirements (Linux or macOS):
#   - bazelisk (https://github.com/bazelbuild/bazelisk)
#   - Android NDK (for oss_android config)
#   - JDK 17+
#
# Usage:
#   ./scripts/build-mozc-android.sh [mozc_src_dir]
#
# Outputs:
#   mozc-engine/src/main/jniLibs/arm64-v8a/libmozc.so
#   mozc-engine/src/main/assets/mozc.data

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOZC_SRC="${1:-${MOZC_SRC:-$ROOT_DIR/third_party/mozc/src}}"
ABI="${MOZC_ABI:-arm64-v8a}"

JNI_OUT="$ROOT_DIR/mozc-engine/src/main/jniLibs/$ABI"
ASSET_OUT="$ROOT_DIR/mozc-engine/src/main/assets"

if [[ ! -d "$MOZC_SRC" ]]; then
  echo "Mozc source not found: $MOZC_SRC" >&2
  echo "Clone https://github.com/google/mozc and pass the src path, e.g.:" >&2
  echo "  git clone https://github.com/google/mozc.git third_party/mozc" >&2
  echo "  ./scripts/build-mozc-android.sh third_party/mozc/src" >&2
  exit 1
fi

export USE_BAZEL_VERSION="${USE_BAZEL_VERSION:-8.2.1}"

echo "==> Building libmozc.so (Android) in $MOZC_SRC"
(
  cd "$MOZC_SRC"
  bazelisk build package \
    --config=oss_android \
    --config=release_build
)

echo "==> Building mozc.data (Linux host)"
(
  cd "$MOZC_SRC"
  bazelisk build //data_manager/oss:mozc_dataset_for_oss \
    --config=oss_linux \
    --compilation_mode=opt
)

mkdir -p "$JNI_OUT" "$ASSET_OUT"

echo "==> Extracting libmozc.so"
TMP_ZIP="$(mktemp -d)"
unzip -q "$MOZC_SRC/bazel-bin/android/jni/native_libs.zip" -d "$TMP_ZIP"
SO_PATH="$(find "$TMP_ZIP" -path "*/$ABI/libmozc.so" | head -n 1)"
if [[ -z "$SO_PATH" ]]; then
  echo "libmozc.so for $ABI not found in native_libs.zip" >&2
  exit 1
fi
cp "$SO_PATH" "$JNI_OUT/libmozc.so"

echo "==> Copying mozc.data"
cp "$MOZC_SRC/bazel-bin/data_manager/oss/mozc.data" "$ASSET_OUT/mozc.data"

echo "Done."
echo "  $JNI_OUT/libmozc.so"
echo "  $ASSET_OUT/mozc.data"
