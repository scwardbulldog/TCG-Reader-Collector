# Architecture

A map of how the code is organised, for someone about to change it. Vocabulary is in [GLOSSARY.md](../GLOSSARY.md); behaviour is in the specs ([v1](spec/v1.md), [v2](spec/v2.md)); the reasons behind big choices are in [docs/adr/](adr/). This page does not repeat them.

Parts that v2 is changing are marked **v2** and point at the spec rather than describing today's code.

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

The Catalog and the Collection are stored apart: a Catalog refresh never touches the Collection ([ADR 0003](adr/0003-phone-only-storage-no-backend.md)). **v2** changes the tables, the refresh rules and the migration: see [v2 spec](spec/v2.md) sections 4 and 5 and [ADR 0004](adr/0004-v2-card-model-derived-card-bandai-authoritative.md).

## Packages in `domain`

| Package | Responsibility |
| --- | --- |
| `tcg.domain` | Printing, Printing identifier, Variant, Owned Copy, Location, and the store interfaces |
| `tcg.domain.onepiece` | One Piece rules: Base Number recognition in OCR text, Variant from the ID suffix (**v2**: Variant becomes a published field) |
| `tcg.domain.catalog` | Parsing a Catalog file and `CatalogUpdater` (format: [catalog-format.md](catalog-format.md); **v2**: new file version) |
| `tcg.domain.collection` | Collection list and filters, Printing detail, Custom Printings, last-known Printings (**v2**: replaced by a flag on the one Printing table), CSV export |
| `tcg.domain.scanning` | Scan Session, lookup and manual search, `BaseNumberLocker`, Bulk Add (`Batch`, `BulkCapture`) |
| `tcg.domain.backup` | The versioned JSON backup format, validation, atomic restore, reminders |

## Packages in `app`

| Package | Responsibility |
| --- | --- |
| `tcg.app` | `MainActivity` (wiring, file pickers, Catalog refresh trigger), `TcgApplication` (image loader) |
| `tcg.app.storage` | Room database, DAOs, entities, the Room store implementations |
| `tcg.app.catalog` | `GitHubCatalogSource`: ETag-based download from the private Catalog repo |
| `tcg.app.scanning` | CameraX + ML Kit scanner, photo cache, Language preference |
| `tcg.app.ui` | Compose screens and the view models behind them |

## How a scan becomes an Owned Copy

1. `CameraXCardScanner` (Single) emits `BaseNumberRead`s from the on-screen card outline. `BaseNumberLocker` turns reads into a lock by voting (`DEFAULT_VOTES`). A lock only fills the text field; the collector taps Look up.
2. `ScanSessionInput.lookup` finds candidate Printings for the Base Number in the session's current Language, from the Catalog and the Collection's Custom Printings. One match is preselected, never added without a tap.
3. The collector confirms (count, condition, Location). `ScanSessionInput.add` writes all copies through `CollectionStore.addAll`, atomically, and remembers the Location for the next add.
4. In Bulk Add, photos are read in the background by `BulkCapture` via `PhotoSource`, merged into `Batch` rows (resolved, ambiguous, failed), and nothing reaches the Collection until "Add all".

Today the state that ties this together (the open confirmation, Language switching, discard prompts, Bulk Add mode) lives in `ScanViewModel`, not in `domain`.

## Where to look when changing X

| Change | Start in |
| --- | --- |
| A scan or search rule | `domain/.../scanning/ScanSessionInput.kt` |
| Bulk Add merging or review rows | `domain/.../scanning/Batch.kt`, `BulkCapture.kt` |
| What the Collection list shows | `domain/.../collection/CollectionList.kt` |
| Backup or restore | `domain/.../backup/` (file reading and writing is in `MainActivity`) |
| Catalog download or import | `app/.../catalog/GitHubCatalogSource.kt`, `domain/.../catalog/CatalogUpdater.kt` |
| A new table or column | `app/.../storage/` and a Room migration in `AppDatabase.kt`, with a test in `androidTest` |

## Keeping this page true

Update it in the same commit as the change that makes it wrong; the definition of done in [verification.md](agents/verification.md) says so. After the v2 storage and Card-model slices (E and G in the [v2 spec](spec/v2.md) section 10), remove the **v2** markers and describe the result.
