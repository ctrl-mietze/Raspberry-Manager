#!/data/data/com.termux/files/usr/bin/bash
# Build Raspberry Manager on Android/Termux. No Surface/PC or modification of other projects.
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
LOGROOT="/storage/emulated/0/Download"
[ -d "$LOGROOT" ] || LOGROOT="$HOME"
REPORT="$LOGROOT/RaspberryManager-build.log"
mkdir -p "$LOGROOT"
exec > >(tee "$REPORT") 2>&1
trap 'echo "[!] Build stopped at line $LINENO; log: $REPORT"' ERR
printf '\n=== Raspberry Manager v0.1.0 · Termux build ===\n'
echo "Project: $PROJECT_ROOT"
echo "Log: $REPORT"

if [ -z "${PREFIX:-}" ]; then echo "[!] Run inside Termux on your Samsung."; exit 1; fi
if ! command -v java >/dev/null 2>&1; then
  echo '[i] Installing Java and basic tools...'
  pkg install -y openjdk-21 curl unzip || pkg install -y openjdk-17 curl unzip
fi
if [ ! -x ./gradlew ]; then chmod +x ./gradlew; fi

# Reuse user-installed Android SDK from earlier local Android projects.
if [ -z "${ANDROID_HOME:-}" ]; then
  for sdk in "$HOME/android-sdk" "$HOME/android-sdk-linux" "$HOME/.android/sdk" "$HOME/sdk" "$PREFIX/share/android-sdk"; do
    if [ -f "$sdk/platforms/android-35/android.jar" ]; then export ANDROID_HOME="$sdk"; break; fi
  done
fi
if [ -z "${ANDROID_HOME:-}" ] || [ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]; then
  echo '[!] Android SDK platform android-35 not found in Termux.'
  echo '    Set ANDROID_HOME to your existing SDK and rerun this script.'
  echo '    The source ZIP contains all app code; no external repo required.'
  exit 2
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties

# Apply native Termux AAPT2 only to this Gradle invocation.
AAPT2_ARGS=()
if command -v aapt2 >/dev/null 2>&1; then
  AAPT2_ARGS+=("-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)")
fi

JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
echo "Java: $JAVA_VERSION"
echo "Android SDK: $ANDROID_HOME"
./gradlew --no-daemon --stacktrace "${AAPT2_ARGS[@]}" :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -s "$APK" ]; then echo '[!] No built APK after Gradle completed.'; exit 3; fi
OUT="$LOGROOT/RaspberryManager-v0.1.0-debug.apk"
cp "$APK" "$OUT"
sha256sum "$OUT"
ls -lh "$OUT"
echo "[OK] APK: $OUT"
echo "[OK] Build log: $REPORT"
