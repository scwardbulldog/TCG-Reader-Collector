# TCG Reader Collector

A personal Android app that inventories One Piece cards by scanning them. See [the v1 spec](docs/spec/v1.md), [the Catalog file format](docs/catalog-format.md) and [GLOSSARY.md](GLOSSARY.md).

## Layout

- `domain/`: portable Kotlin/JVM module with no Android dependencies. The Printing, Variant and Owned Copy model, the `CatalogStore` / `CollectionStore` interfaces, One Piece rules (`tcg.domain.onepiece`) and the `CardScanner` interface live here. A `FakeCardScanner` is in its test fixtures.
- `app/`: Android app (Kotlin, Jetpack Compose). `tcg.app.storage` has the Room implementation, with the Catalog and Collection in separate tables. CameraX and ML Kit text recognition (bundled Latin model) are declared but not used yet.

## Requirements

- JDK 17
- Android SDK with platform 35. Put its path in `local.properties` (`sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`) or set `ANDROID_HOME`.

## Commands

Run from the repo root (use `./gradlew` on macOS/Linux).

| Task | Command |
| --- | --- |
| Domain tests (JVM, no device) | `.\gradlew.bat :domain:test` |
| One domain test class | `.\gradlew.bat :domain:test --tests "tcg.domain.scanning.CardScannerTest"` |
| Storage tests (Room, on the attached phone) | `.\gradlew.bat :app:connectedDebugAndroidTest` |
| Build debug APK | `.\gradlew.bat :app:assembleDebug` |

`connectedDebugAndroidTest` needs the phone attached (`adb devices`). Gradle uninstalls the app from the phone afterwards, so run `:app:installDebug` again to reinstall it.

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload onto the Pixel 10 Pro

1. On the phone, enable Developer options and USB debugging, then connect it over USB and accept the debugging prompt.
2. Check it is listed: `adb devices`.
3. Install: `.\gradlew.bat :app:installDebug`, or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The app needs no account, backend or Play Store services.

## Test devices

The app's `minSdk` is 33 (Android 13). With several devices attached, Gradle installs on all of them; use `adb -s <serial>` for single-device commands.

| Device | Serial | Android | ABI | Screen | RAM | Role |
| --- | --- | --- | --- | --- | --- | --- |
| Pixel 10 Pro (owner's phone) | 57221FDCH0053C | 17 | arm64 | | | Primary target; scanning prototype was proven here |
| Motorola moto g play - 2023 | ZY22K6NFV6 | 13 (API 33) | armeabi-v7a (32-bit only) | 720x1600, 280 dpi | 2.8 GB | Low-end test device: oldest Android and slowest hardware we support, with a rear camera with autofocus and flash. No screen lock; if the screen is off, `adb shell input keyevent KEYCODE_WAKEUP` and `adb shell wm dismiss-keyguard`. |

Run `.\gradlew.bat :app:connectedDebugAndroidTest` and `:app:installDebug` against whichever is attached. The Catalog download needs a build-time token (see below), so a tokenless build on a device shows "No Catalog token was built into this app".

## Catalog download

On launch, and from the menu's "Update catalog", the app downloads `onepiece-en.json` and `onepiece-jp.json` from the private `scwardbulldog/TCG-Catalog` repo and stores them in the local database (see [the Catalog file format](docs/catalog-format.md)). A failed or malformed download keeps the previous Catalog. The ETag of each file is its stored version, so an unchanged Catalog is not downloaded again.

Access uses a read-only, fine-grained GitHub token (Contents: read, `TCG-Catalog` only), set at build time in `local.properties` (`catalog.token=...`) or the `CATALOG_TOKEN` environment variable. It is the app's only credential and must never be committed.
