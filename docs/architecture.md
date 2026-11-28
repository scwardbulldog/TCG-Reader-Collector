# Architecture

A map of how the code is organised, for someone about to change it. Vocabulary is in [GLOSSARY.md](../GLOSSARY.md); behaviour is in the specs ([v1](spec/v1.md), [v2](spec/v2.md)); the reasons behind big choices are in [docs/adr/](adr/). This page does not repeat them.

## Two modules

| Module | Holds | Rule |
| --- | --- | --- |
| `domain/` | The rules: model, Scan Session, Collection views, backup, Catalog update. Kotlin/JVM. | No Android dependencies. Tested on the JVM with in-memory fakes (`domain/src/testFixtures`). |
| `app/` | Android only: Room storage, CameraX/ML Kit, Compose screens, view models, `MainActivity`. | Implements the `domain` interfaces below and renders `domain` state. Has no unit tests, only `androidTest` for storage. |

Dependency direction: `app` depends on `domain`, never the reverse.

## Seams

Each interface below is defined in `domain` and implemented in `app`; tests use a fake from `testFixtures`. When you need a new device-specific capability, add an interface here rather than importing Android types into `domain`.

| Interface | What it hides | `app` implementation | Test fake |
| --- | --- | --- | --- |
| `CatalogStore` | The stored Catalog | `RoomCatalogStore` | `InMemoryCatalogStore` |
| `CollectionStore` | Owned Copies, Locations, Custom Printings, snapshot/restore | `RoomCollectionStore` | `InMemoryCollectionStore` |
| `BackupStatusStore` | When the last backup was made | `RoomBackupStatusStore` | `InMemoryBackupStatusStore` |
| `CatalogSource` | Where Catalog files come from | `GitHubCatalogSource` | `FakeCatalogSource` |
| `CardScanner` | Reading Base Numbers from the live camera | `CameraXCardScanner` | `FakeCardScanner` |
| `PhotoSource` | Reading a Base Number from a Bulk Add photo, and deleting it | `CachePhotoSource` | `ScriptedSource`, private to `BulkCaptureTest` |
| `LanguageStore` | The remembered scan Language | `SharedPreferencesLanguageStore` | `InMemoryLanguageStore` |
| `CardPhotoStore` | Keeping, finding and deleting the Card photo of a Custom Printing | `FileCardPhotoStore` | `InMemoryCardPhotoStore` |

The Catalog and the Collection are stored apart: a Catalog refresh never touches the Collection's Owned Copies or Locations ([ADR 0003](adr/0003-phone-only-storage-no-backend.md)). Catalog and Custom Printings share one `printing` table, told apart by `origin`. A refresh is a single transaction in `CatalogDao.replaceAll`, planned by the pure `CatalogRefresh`: a dropped Printing that an Owned Copy uses is kept and flagged **No longer in the Catalog**, an unused one is deleted, a listed one takes the new details and clears the flag, and Custom rows are never touched. The JSON backup carries the last-known details of flagged rows (an optional `lastKnownPrintings` field, `schemaVersion` stays 1); a restore inserts them as flagged rows only where the Catalog has no row for that Printing, so the Catalog's details win. Room migration 4 to 5 builds the table from `known_printing` ([v2 spec](spec/v2.md) sections 4 and 5, [ADR 0004](adr/0004-v2-card-model-derived-card-bandai-authoritative.md)). Migration 5 to 6 adds `catalog_version.generatedAt` (the Catalog file's `generatedAt`, shown as the Catalog date: the oldest across languages) and clears the stored ETag so the next launch re-downloads. Deleting a Location sets its copies to Location None in one transaction.

A Custom Printing's **Card photo** is one JPEG (long edge at most 1600 px) in the app-private `files/card-photos/`, named by the Printing's identifier; `Printing.cardPhoto` holds only an opaque reference (`<file name>@<time saved>`) that changes on every save, so a screen reloads the picture after a retake. Room migration 7 to 8 adds the nullable `printing.cardPhoto` column. `CustomPrintings` owns the lifecycle: the photo is saved before the row is written (a failed save leaves the Printing without a photo, or keeps the old one), and it is deleted with the Printing (including the scan-time discard and "Delete both") and when replaced or removed. Change Printing leaves the Printing and its photo alone. The backup does not carry photos (a restore leaves Custom Printings without one and clears the photo folder). Capture is the ML Kit Document Scanner (Play Services, optional: without it the app shows a message and carries on without a photo); its filter mode is the `CARD_PHOTO_SCANNER_MODE` constant in `CardPhotoCapture.kt`.

Search matches a Base Number or name ignoring case and full-width/half-width forms (`containsFolded`); `RoomCatalogStore.search` folds in Kotlin over one Language's rows rather than keeping folded text in the table.

## Packages in `domain`

| Package | Responsibility |
| --- | --- |
| `tcg.domain` | Printing (with its published Variant, Product code and Treatments), Printing identifier, Variant, Owned Copy, Location, and the store interfaces |
| `tcg.domain.onepiece` | One Piece rules: Base Number recognition in OCR text |
| `tcg.domain.catalog` | Parsing a Catalog file (`schemaVersion` 2 only), `CatalogUpdater`, and `CatalogRefresh` (the refresh rules; format: [catalog-format.md](catalog-format.md)) |
| `tcg.domain.collection` | Collection list and filters (a name search also finds the owned Printings of a matching Card in either Language), Printing detail (including Change Printing: one Owned Copy moves to any Printing through `CollectionStore.move`, and `update` never touches the Printing), Custom Printings, `Cards` and `Card` (derived from the Printings; Card names; name search in both Languages with the full-width fold; natural Base Number order; the proposal order of a Card's Printings), `CardView` (the read-only Card view: Printings by Language then Variant with owned counts), `PrintingTitles` (a Printing's English title with Japanese subtext, plus Treatment and Product code text helpers), `PrintingResolver` (Catalog or Custom row for an Owned Copy), CSV export (with the Card's `English name` column) |
| `tcg.domain.scanning` | Scan Session, the typed search (`find`: Base Number to proposals, else Card-first name search with `CardMatch` rows), `BaseNumberLocker`, Bulk Add (`Batch`, `BulkCapture`; a failed row is fixed by Base Number or name) |
| `tcg.domain.backup` | The versioned JSON backup format, validation, atomic restore, reminders |

## Packages in `app`

| Package | Responsibility |
| --- | --- |
| `tcg.app` | `MainActivity` (wiring, file pickers, Catalog refresh trigger), `TcgApplication` (image loader) |
| `tcg.app.storage` | Room database, DAOs, entities, the Room store implementations |
| `tcg.app.catalog` | `GitHubCatalogSource`: ETag-based download from the private Catalog repo |
| `tcg.app.scanning` | CameraX + ML Kit scanner, photo cache, Language preference, `ScanDebugLog` (debug builds only) |
| `tcg.app.ui` | Compose screens and the view models behind them; `CardPhotoCapture.kt` (Document Scanner launch, photo tips) and `PrintingImage` (Card photo, else official image) |

## How a scan becomes an Owned Copy

1. `CameraXCardScanner` (Single) emits `BaseNumberRead`s from the on-screen card outline. `BaseNumberLocker` turns reads into a lock by voting (`DEFAULT_VOTES`). A lock only fills the text field; the collector taps Look up.
2. `ScanSessionInput.find` is the one typed search. A Base Number with Printings in the session's current Language goes straight to its proposals (`lookup`: Catalog plus the Collection's Custom Printings, ordered by Variant, Product code, then Release Product; one match is preselected, never added without a tap). Anything else lists Cards (`searchCards`: one row each, natural Base Number order); choosing one runs `lookup` on its Base Number. If nothing matches, the collector can create a Custom Printing or skip.
3. The collector confirms (count, condition, Location). `ScanSessionInput.add` writes all copies through `CollectionStore.addAll`, atomically, and remembers the Location for the next add.
4. In Bulk Add, photos are read in the background by `BulkCapture` via `PhotoSource`, merged into `Batch` rows (resolved, ambiguous, failed), and nothing reaches the Collection until "Add all".

In debug builds, `ScanDebugLog` records the live scan's last few outline crops with their OCR text and parsed Base Number whenever a number locks or text is read but no Base Number is found. Records are app-private under `files/scan-debug/` (newest 30 kept, wiped at start-up); pull them with `adb exec-out run-as tcg.app tar -c -C files scan-debug`. Release builds have no log.

Today the state that ties this together (the open confirmation, Language switching, discard prompts, Bulk Add mode) lives in `ScanViewModel`, not in `domain`.

## Where to look when changing X

| Change | Start in |
| --- | --- |
| A scan or search rule | `domain/.../scanning/ScanSessionInput.kt` |
| Card grouping, Card names, name matching, proposal or Base Number order | `domain/.../collection/Card.kt`, `domain/.../SearchText.kt` |
| The Card view | `domain/.../collection/CardView.kt`, `app/.../ui/CardViewScreen.kt`; navigation is the back stack in `MainActivity` |
| How a Printing is titled (English title, Japanese subtext) | `domain/.../collection/PrintingTitle.kt`; the screens read it through `rememberPrintingTitle` in `app/.../ui/Labels.kt` |
| Bulk Add merging or review rows | `domain/.../scanning/Batch.kt`, `BulkCapture.kt` |
| What the Collection list shows | `domain/.../collection/CollectionList.kt` |
| Backup or restore | `domain/.../backup/` (file reading and writing is in `MainActivity`) |
| Catalog download or import | `app/.../catalog/GitHubCatalogSource.kt`, `domain/.../catalog/CatalogUpdater.kt` |
| A new table or column | `app/.../storage/` and a Room migration in `AppDatabase.kt`, with a test in `androidTest` |
| The Card photo of a Custom Printing | `domain/.../collection/CustomPrintings.kt`, `app/.../storage/FileCardPhotoStore.kt`, `app/.../ui/CardPhotoCapture.kt` |

## Keeping this page true

Update it in the same commit as the change that makes it wrong; the definition of done in [verification.md](agents/verification.md) says so.
