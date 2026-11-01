# Raspberry Manager — Android

The Android edition is a Kotlin + Jetpack Compose application, package `dev.mietze.raspberrymanager`. The current public snapshot is **v10.3.0** (`versionCode 100300`), developed from the v10.2.0 Workbench baseline.

## Project layout

- [`source/app/src/main/java/dev/mietze/piimager/`](source/app/src/main/java/dev/mietze/piimager/) – native screens, imaging and connection logic.
- [`source/app/src/main/java/dev/mietze/piimager/workbench/`](source/app/src/main/java/dev/mietze/piimager/workbench/) – graph models, editor, catalog, validation and export logic.
- [`source/app/src/test/`](source/app/src/test/) – JVM regression tests.
- [`UPSTREAM.md`](UPSTREAM.md) / [`LICENSE-RPI-IMAGER.txt`](LICENSE-RPI-IMAGER.txt) – upstream Raspberry Pi Imager notices.

## Build

Requirements: JDK 17, Android SDK 35.

```bash
cd android/source
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

On **Termux**, use `bash scripts/build-current-on-termux.sh` to select native AAPT2 automatically. Standard Linux/Windows builds use Maven-provided AAPT2.

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

The original public release APK is available through **[GitHub Releases](https://github.com/ctrl-mietze/Raspberry-Manager/releases/latest)**. Keep in mind that the local debug signing key may not match the previously installed application. Preserve app data before changing installed versions.

## UI and behavior

- Direct launch to Raspberry Manager home without the old startup chooser.
- Native USB/SD Imager available from the upper-right USB action.
- Normal Pi Magic Image Designer with **Ready · Continue** and separate diagnostic vs. flash flow.
- Responsive advanced Workbench with search, selection, arrangement, validation and export tools.
- Native SSH profile management.
- Raspberry Pi Connect presented through native controls where possible, keeping the official login/session client inside a separate app WebView.

**Important boundaries:** EmulateLoad is a dependency/design dry-run, not a full Raspberry Pi boot emulation. Planning a multi-OS stack does not automatically generate a flashable raw image. Hardware writes require explicit target/erase confirmation.

## Recorded tests

The v10.3.0 package was built with Gradle and validated with 279 JVM tests (278 pass, 1 skipped), 33 Connect DOM tests and 18 clipboard tests; signing certificate was matched against the v10.2.0 APK.

The tests did not certify every live Pi Connect account, orientation combination or USB storage hardware.

For the complete cross-platform feature overview, see [README.md](../README.md).
