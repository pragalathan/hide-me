#!/usr/bin/env bash
# Build the release APK, align it, and sign it with hideme-release.keystore.
set -euo pipefail

cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Applications/Android/Sdk}}"
BUILD_TOOLS="$SDK/build-tools/36.1.0"
if [[ ! -x "$BUILD_TOOLS/apksigner" ]]; then
  echo "apksigner not found at $BUILD_TOOLS" >&2
  exit 1
fi

./gradlew :app:assembleRelease

UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED="app/build/outputs/apk/release/app-release-aligned.apk"
SIGNED="HideMe-1.0.apk"

"$BUILD_TOOLS/zipalign" -f -p 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks hideme-release.keystore \
  --ks-key-alias hideme \
  --out "$SIGNED" \
  "$ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose "$SIGNED"

echo "Signed $SIGNED"
