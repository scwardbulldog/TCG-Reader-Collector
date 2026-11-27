package tcg.app.storage

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import tcg.domain.CatalogStore
import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.backup.BackupStatus
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogAndCollectionStorageTest {
    private lateinit var db: AppDatabase
    private lateinit var catalog: CatalogStore
    private lateinit var collection: CollectionStore

    @Before
    fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        catalog = db.catalogStore()
        collection = db.collectionStore()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replacing_catalog_rows_leaves_every_owned_copy_unchanged() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)))
        collection.add(NewOwnedCopy(zoro, notes = "sleeved"))
        val before = collection.ownedCopies(zoro)

        catalog.replaceCatalog(Game.OnePiece, Language.English, emptyList())

        assertEquals(before, collection.ownedCopies(zoro))
    }

    @Test
    fun a_dropped_printing_an_owned_copy_uses_stays_flagged_with_its_details_and_an_unused_one_is_deleted() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro), printing(nami)))
        val saved = catalog.find(zoro)!!
        collection.add(NewOwnedCopy(zoro))

        catalog.replaceCatalog(Game.OnePiece, Language.English, emptyList())

        assertEquals(saved.copy(noLongerInCatalog = true), catalog.find(zoro))
        assertNull(catalog.find(nami))
        assertEquals(emptyList(), catalog.findByBaseNumber(Game.OnePiece, Language.English, "OP01-001"))
        assertNull(catalog.find(PrintingIdentifier(Game.OnePiece, Language.English, "OP99-999")))
    }

    @Test
    fun a_refresh_gives_an_unchanged_id_new_details_and_clears_the_flag_of_a_returning_printing() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro), printing(nami)))
        collection.add(NewOwnedCopy(zoro))
        collection.add(NewOwnedCopy(nami))
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(nami)))
        assertEquals(true, catalog.find(zoro)?.noLongerInCatalog)

        catalog.replaceCatalog(
            Game.OnePiece, Language.English,
            listOf(printing(zoro).copy(name = "Renamed"), printing(nami).copy(name = "Nami (fixed)")),
        )

        assertEquals(printing(zoro).copy(name = "Renamed"), catalog.find(zoro))
        assertEquals("Nami (fixed)", catalog.find(nami)?.name)
    }

    @Test
    fun variant_product_code_and_treatments_are_stored_and_unknown_treatments_stay_unknown() = runTest {
        val a = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val b = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val c = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-002")
        val tagged = printing(a).copy(variant = Variant.AlternateArt, productCode = "OP-01", treatments = listOf("Wanted Poster", "Manga"))
        val plain = printing(b).copy(variant = Variant.Normal, treatments = emptyList())
        val unknown = printing(c).copy(variant = Variant.Reprint, treatments = null)

        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(tagged, plain, unknown))

        assertEquals(tagged, catalog.find(a))
        assertEquals(plain, catalog.find(b))
        assertEquals(unknown, catalog.find(c))
    }

    @Test
    fun a_refresh_never_touches_a_custom_printing() = runTest {
        val custom = PrintingIdentifier(Game.OnePiece, Language.English, "custom:1")
        val milk = printing(custom).copy(baseNumber = "OP01-001", releaseProduct = "Custom", name = "Milk Box Stamp")
        collection.saveCustomPrinting(milk)
        collection.add(NewOwnedCopy(custom))

        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016"))))
        catalog.replaceCatalog(Game.OnePiece, Language.English, emptyList())

        assertEquals(milk, collection.customPrinting(custom))
        assertNull(catalog.find(custom))
    }

    @Test
    fun an_owned_copy_can_reference_a_printing_absent_from_the_catalog() = runTest {
        val unknown = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP99-999")

        val copy = collection.add(NewOwnedCopy(unknown))

        assertNotNull(copy)
        assertEquals(listOf(copy), collection.ownedCopies(unknown))
    }

    @Test
    fun a_new_owned_copy_defaults_to_near_mint() = runTest {
        val id = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")

        val copy = collection.add(NewOwnedCopy(id))

        assertEquals(Condition.NearMint, copy.condition)
        assertEquals(Condition.NearMint, collection.ownedCopies(id).single().condition)
    }

    @Test
    fun counts_are_derived_per_printing_and_keep_languages_separate() = runTest {
        val en = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val jp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001")
        collection.add(NewOwnedCopy(en))
        collection.add(NewOwnedCopy(en))
        collection.add(NewOwnedCopy(jp))

        assertEquals(mapOf(en to 2, jp to 1), collection.countsByPrinting())
    }

    @Test
    fun the_same_bandai_id_in_two_languages_is_two_printings() = runTest {
        val en = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val jp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001_p1")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(en).copy(name = "EN Zoro")))
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(printing(jp).copy(name = "JP Zoro")))

        assertEquals("EN Zoro", catalog.find(en)?.name)
        assertEquals("JP Zoro", catalog.find(jp)?.name)
        assertEquals(listOf(en), catalog.findByBaseNumber(Game.OnePiece, Language.English, "OP01-001").map { it.identifier })
    }

    @Test
    fun a_failed_bulk_add_leaves_no_owned_copies_behind() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val binder = collection.createLocation("Binder 1")

        val failed = runCatching {
            collection.addAll(
                listOf(NewOwnedCopy(zoro, location = binder.id), NewOwnedCopy(zoro, location = LocationId(9999))),
            )
        }

        assertTrue(failed.isFailure)
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun search_matches_base_number_or_name_in_one_language_ignoring_case_and_width() = runTest {
        val en = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val jp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(en).copy(name = "Zoro")))
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(printing(jp).copy(name = "Ｄ・ゾロ＆ロー")))

        assertEquals(listOf(en), catalog.search(Game.OnePiece, Language.English, "zor").map { it.identifier })
        assertEquals(listOf(en), catalog.search(Game.OnePiece, Language.English, "op01-0").map { it.identifier })
        assertEquals(emptyList(), catalog.search(Game.OnePiece, Language.English, "%"))
        assertEquals(emptyList(), catalog.search(Game.OnePiece, Language.English, "OP01_001"))
        assertEquals(listOf(jp), catalog.search(Game.OnePiece, Language.Japanese, "d・ゾロ&").map { it.identifier })
    }

    @Test
    fun renaming_a_location_applies_to_every_owned_copy_in_it() = runTest {
        val id = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(id, location = binder.id))
        collection.add(NewOwnedCopy(id, location = binder.id))

        collection.renameLocation(binder.id, "Binder One")

        assertEquals(listOf(Location(binder.id, "Binder One")), collection.locations())
        assertEquals(listOf(binder.id, binder.id), collection.ownedCopies(id).map { it.location })
    }

    @Test
    fun deleting_a_location_in_use_gives_its_owned_copies_location_none() = runTest {
        val id = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(id, location = binder.id))
        collection.add(NewOwnedCopy(id))

        assertEquals(1, collection.copiesIn(binder.id))
        collection.deleteLocation(binder.id)

        assertEquals(emptyList(), collection.locations())
        assertEquals(listOf(null, null), collection.ownedCopies(id).map { it.location })
    }

    @Test
    fun the_catalog_generation_date_is_the_oldest_stored_one() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val jp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001_p1")
        assertNull(catalog.catalogGeneratedAt())
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)), generatedAt = Instant.parse("2026-02-01T00:00:00Z"))
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(printing(jp)), generatedAt = Instant.parse("2026-01-01T00:00:00Z"))

        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), catalog.catalogGeneratedAt())
    }

    @Test
    fun updating_an_owned_copy_changes_only_that_copy_and_deleting_removes_only_it() = runTest {
        val id = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val binder = collection.createLocation("Binder 1")
        val first = collection.add(NewOwnedCopy(id))
        val second = collection.add(NewOwnedCopy(id))

        val edited = first.copy(condition = Condition.Damaged, location = binder.id, notes = "creased")
        collection.update(edited)

        assertEquals(listOf(edited, second), collection.ownedCopies(id))

        collection.delete(first.id)

        assertEquals(listOf(second), collection.ownedCopies(id))
    }

    @Test
    fun moving_an_owned_copy_changes_only_its_printing() = runTest {
        val id = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val target = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001")
        val binder = collection.createLocation("Binder 1")
        val moved = collection.add(NewOwnedCopy(id, Condition.Damaged, binder.id, "creased"))
        val stays = collection.add(NewOwnedCopy(id))

        collection.move(moved.id, target)

        assertEquals(listOf(stays), collection.ownedCopies(id))
        assertEquals(listOf(moved.copy(printing = target)), collection.ownedCopies(target))
    }

    @Test
    fun the_catalog_version_is_stored_with_the_printings_per_language() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        assertNull(catalog.catalogVersion(Game.OnePiece, Language.English))

        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)), version = "v1")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)), version = "v2")

        assertEquals("v2", catalog.catalogVersion(Game.OnePiece, Language.English))
        assertNull(catalog.catalogVersion(Game.OnePiece, Language.Japanese))
    }

    @Test
    fun replacing_the_collection_swaps_everything_and_keeps_ids() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val old = db.collectionStore()
        old.createLocation("Old box")
        old.add(NewOwnedCopy(zoro))
        val replacement = CollectionSnapshot(
            locations = listOf(Location(LocationId(42), "Binder 1")),
            copies = listOf(
                OwnedCopy(OwnedCopyId(7), zoro, Condition.Damaged, LocationId(42), "creased"),
                OwnedCopy(OwnedCopyId(8), zoro, Condition.Damaged, LocationId(42), "creased"),
            ),
        )

        collection.replaceCollection(replacement)

        assertEquals(replacement, collection.snapshot())
    }

    @Test
    fun custom_printings_are_listed_per_language_survive_a_replace_that_carries_them_and_can_be_deleted() = runTest {
        val custom = PrintingIdentifier(Game.OnePiece, Language.English, "custom:1")
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val milk = printing(custom).copy(baseNumber = "OP01-001", releaseProduct = "Custom", name = "Milk Box Stamp")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)))
        collection.saveCustomPrinting(milk)

        assertEquals(listOf(milk), collection.customPrintings(Game.OnePiece, Language.English))
        assertEquals(emptyList(), collection.customPrintings(Game.OnePiece, Language.Japanese))
        assertEquals(listOf(milk), collection.snapshot().customPrintings)

        collection.replaceCollection(CollectionSnapshot(emptyList(), emptyList(), listOf(milk)))
        assertEquals(listOf(milk), collection.customPrintings(Game.OnePiece, Language.English))
        assertNotNull(catalog.find(zoro))

        collection.deleteCustomPrinting(custom)
        assertNull(collection.customPrinting(custom))
        assertNotNull(catalog.find(zoro))
    }

    @Test
    fun a_flagged_printing_is_in_the_snapshot_and_a_restore_brings_it_back_flagged_only_where_the_catalog_lacks_it() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro), printing(nami)))
        collection.add(NewOwnedCopy(zoro))
        collection.add(NewOwnedCopy(nami))
        catalog.replaceCatalog(Game.OnePiece, Language.English, emptyList())
        val backup = collection.snapshot()
        val lastKnown = printing(zoro).copy(noLongerInCatalog = true)
        assertEquals(setOf(lastKnown, printing(nami).copy(noLongerInCatalog = true)), backup.lastKnownPrintings.toSet())

        // Fresh install, Catalog lacks both: both come back flagged with their last-known name.
        val fresh = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        fresh.collectionStore().replaceCollection(backup)
        assertEquals(lastKnown, fresh.catalogStore().find(zoro))
        fresh.close()

        // The Catalog has Zoro (with newer details): its details win and it is not flagged; Nami is flagged.
        val withZoro = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        val listed = printing(zoro).copy(name = "Zoro (current)")
        withZoro.catalogStore().replaceCatalog(Game.OnePiece, Language.English, listOf(listed))
        withZoro.collectionStore().replaceCollection(backup)
        assertEquals(listed, withZoro.catalogStore().find(zoro))
        assertEquals(true, withZoro.catalogStore().find(nami)?.noLongerInCatalog)
        withZoro.close()
    }

    @Test
    fun a_restored_flagged_printing_takes_the_catalogs_details_when_a_later_refresh_lists_it() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        collection.replaceCollection(
            CollectionSnapshot(emptyList(), listOf(OwnedCopy(OwnedCopyId(1), zoro, Condition.NearMint, null, "")), lastKnownPrintings = listOf(printing(zoro))),
        )
        assertEquals(true, catalog.find(zoro)?.noLongerInCatalog)

        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro).copy(name = "Zoro (current)")))

        assertEquals(printing(zoro).copy(name = "Zoro (current)"), catalog.find(zoro))
    }

    @Test
    fun a_flagged_printing_no_owned_copy_uses_any_more_is_not_carried_by_the_snapshot_or_a_restore() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)))
        val copy = collection.add(NewOwnedCopy(zoro))
        catalog.replaceCatalog(Game.OnePiece, Language.English, emptyList())
        collection.delete(copy.id)

        assertEquals(emptyList(), collection.snapshot().lastKnownPrintings)

        val stray = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-099")
        db.collectionStore().replaceCollection(CollectionSnapshot(emptyList(), emptyList(), lastKnownPrintings = listOf(printing(stray))))
        assertNull(catalog.find(stray))
    }

    @Test
    fun a_replace_that_fails_part_way_leaves_the_old_collection_intact() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val custom = PrintingIdentifier(Game.OnePiece, Language.English, "custom:1")
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(zoro, location = binder.id, notes = "keep me"))
        collection.saveCustomPrinting(printing(custom))
        val before = collection.snapshot()
        val broken = CollectionSnapshot(
            locations = emptyList(),
            copies = listOf(OwnedCopy(OwnedCopyId(1), zoro, Condition.NearMint, LocationId(9999), "")),
        )

        val failed = runCatching { collection.replaceCollection(broken) }

        assertTrue(failed.isFailure)
        assertEquals(before, collection.snapshot())
        assertNotNull(collection.customPrinting(custom))
    }

    @Test
    fun backup_status_is_stored_and_replaced() = runTest {
        val store = db.backupStatusStore()
        assertEquals(BackupStatus(), store.load())

        val saved = BackupStatus(Instant.ofEpochMilli(1_700_000_000_000), "abc123", restoredSinceBackup = true)
        store.save(saved)
        assertEquals(saved, store.load())

        store.save(BackupStatus())
        assertEquals(BackupStatus(), store.load())
    }

    private fun printing(id: PrintingIdentifier) = Printing(
        identifier = id,
        baseNumber = id.sourceId.substringBefore('_'),
        releaseProduct = "Romance Dawn",
        rarity = "L",
        name = "Roronoa Zoro",
        imageUrl = "https://example.invalid/${id.sourceId}.png",
        variant = null,
    )
}
