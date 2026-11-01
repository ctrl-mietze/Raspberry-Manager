#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
chmod +x gradlew
OUT="/storage/emulated/0/Download/RaspberryManager-Rebuilt"
mkdir -p "$OUT"
exec > >(tee "$OUT/build.log") 2>&1
# Native ARM64 AAPT2 on Termux; standard Linux/Windows builds use Maven AAPT2.
AAPT2_ARGS=()
if command -v aapt2 >/dev/null 2>&1; then
  AAPT2_ARGS+=("-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)")
fi
./gradlew --no-daemon --console=plain "${AAPT2_ARGS[@]}" :app:assembleDebug :app:testDebugUnitTest
cp app/build/outputs/apk/debug/app-debug.apk "$OUT/RaspberryManager-VX-debug.apk"
apksigner verify --verbose "$OUT/RaspberryManager-VX-debug.apk"
echo "APK: $OUT/RaspberryManager-VX-debug.apk"
