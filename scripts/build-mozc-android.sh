#!/usr/bin/env bash
# Build Mozc native library and dictionary for Android, then copy into mozc-engine module.
#
# Requirements (Linux or macOS):
#   - bazelisk (https://github.com/bazelbuild/bazelisk)
#   - Python 3.12+
#   - C++ compiler (GCC or clang)
#   - Android NDK (downloaded by build_tools/update_deps.py)
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
SKIP_UPDATE_DEPS="${SKIP_UPDATE_DEPS:-0}"

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

if [[ "$SKIP_UPDATE_DEPS" != "1" ]]; then
  echo "==> Downloading Mozc build dependencies (Android NDK, etc.)"
  (
    cd "$MOZC_SRC"
    python3 build_tools/update_deps.py
  )
fi

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
  # Avoid Android NDK toolchain resolution for the host dataset build.
  ANDROID_NDK_HOME= bazelisk build //data_manager/oss:mozc_dataset_for_oss \
    --config=oss_linux \
    --compilation_mode=opt
)

mkdir -p "$JNI_OUT" "$ASSET_OUT"

echo "==> Extracting libmozc.so"
TMP_ZIP="$(mktemp -d)"
trap 'rm -rf "$TMP_ZIP"' EXIT
unzip -q "$MOZC_SRC/bazel-bin/android/jni/native_libs.zip" -d "$TMP_ZIP"
SO_PATH="$(find "$TMP_ZIP" -path "*/$ABI/libmozc.so" | head -n 1)"
if [[ -z "$SO_PATH" ]]; then
  echo "libmozc.so for $ABI not found in native_libs.zip" >&2
  find "$TMP_ZIP" -name "libmozc.so" >&2 || true
  exit 1
fi
cp "$SO_PATH" "$JNI_OUT/libmozc.so"

echo "==> Copying mozc.data"
cp "$MOZC_SRC/bazel-bin/data_manager/oss/mozc.data" "$ASSET_OUT/mozc.data"

echo "Done."
echo "  $JNI_OUT/libmozc.so"
echo "  $ASSET_OUT/mozc.data"
