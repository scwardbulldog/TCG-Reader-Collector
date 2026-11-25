# TCG Reader Collector

A personal Android app that inventories One Piece cards by scanning them. See [the v1 spec](docs/spec/v1.md) and [GLOSSARY.md](GLOSSARY.md).

## Layout

- `domain/`: portable Kotlin/JVM module with no Android dependencies. Printings, Owned Copies, matching rules and the `CardScanner` interface live here. A `FakeCardScanner` is in its test fixtures.
- `app/`: Android app (Kotlin, Jetpack Compose). CameraX and ML Kit text recognition (bundled Latin model) are declared but not used yet.

## Requirements

- JDK 17
- Android SDK with platform 35. Put its path in `local.properties` (`sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`) or set `ANDROID_HOME`.

## Commands

Run from the repo root (use `./gradlew` on macOS/Linux).

| Task | Command |
| --- | --- |
| Domain tests (JVM, no device) | `.\gradlew.bat :domain:test` |
| One domain test class | `.\gradlew.bat :domain:test --tests "tcg.domain.scanning.CardScannerTest"` |
| Build debug APK | `.\gradlew.bat :app:assembleDebug` |

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload onto the Pixel 10 Pro

1. On the phone, enable Developer options and USB debugging, then connect it over USB and accept the debugging prompt.
2. Check it is listed: `adb devices`.
3. Install: `.\gradlew.bat :app:installDebug`, or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The app needs no account, backend or Play Store services.
