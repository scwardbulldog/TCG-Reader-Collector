# TCG Reader Collector

A personal Android app that inventories One Piece cards by scanning them. See [the v1 spec](docs/spec/v1.md), [the Catalog file format](docs/catalog-format.md) and [GLOSSARY.md](GLOSSARY.md).

## Layout

- `domain/`: portable Kotlin/JVM module with no Android dependencies. The Printing, Variant and Owned Copy model, the `CatalogStore` / `CollectionStore` interfaces, One Piece rules (`tcg.domain.onepiece`), the Collection list and Printing detail logic (`tcg.domain.collection`), the Scan Session rules (`tcg.domain.scanning`: setup, candidate resolution, manual search, add, and the `BaseNumberLocker` that turns Base Number reads into locks by voting, with the vote count in `BaseNumberLocker.DEFAULT_VOTES`) and the `CardScanner` interface live here, as does the backup (`tcg.domain.backup`: the versioned JSON format `BackupFormat`, validation, `CollectionBackup` for backup, atomic restore and reminders). A `FakeCardScanner` and in-memory stores are in its test fixtures.
- `app/`: Android app (Kotlin, Jetpack Compose). `tcg.app.storage` has the Room implementation, with the Catalog and Collection in separate tables. `tcg.app.ui` has the Collection list, Printing detail and Scan Session screens; the app opens on the Collection list, and Add starts a Scan Session that reads Base Numbers from the live camera (`tcg.app.scanning`: CameraX and ML Kit's bundled Latin text recognition behind `CardScanner`, analysing only the on-screen card outline) with manual search / type-in alongside. The menu's Export CSV writes the whole Collection for Google Sheets, and Backup / Restore save and load the versioned JSON backup through the document picker (`BackupViewModel`, `BackupUi`); a reminder banner shows when the Collection has changed since the last backup or after a restore. Printing images (`tcg.app.ui.PrintingImage`, Coil) load from the official Bandai URL on first view and are cached on disk (`TcgApplication.newImageLoader`); nothing is prefetched or bundled. Debug builds also have a menu entry, "Add sample data (debug)", that fills an empty Collection with sample Owned Copies (`app/src/debug`); release builds don't include it.

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

`connectedDebugAndroidTest` needs the phone attached (`adb devices`; if `adb` isn't on your PATH, use `<sdk.dir>\platform-tools\adb.exe`). Gradle uninstalls the app from the phone afterwards, so run `:app:installDebug` again to reinstall it.

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
