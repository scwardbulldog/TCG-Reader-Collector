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
import tcg.domain.DeleteLocationResult
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
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

        assertNull(catalog.find(zoro))
        assertEquals(before, collection.ownedCopies(zoro))
    }

    @Test
    fun last_known_printing_details_survive_a_catalog_refresh_that_drops_the_printing() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro)))
        val saved = catalog.find(zoro)!!

        collection.rememberPrintings(listOf(saved))
        catalog.replaceCatalog(Game.OnePiece, Language.English, emptyList())

        assertNull(catalog.find(zoro))
        assertEquals(saved, collection.lastKnownPrinting(zoro))
        assertNull(collection.lastKnownPrinting(PrintingIdentifier(Game.OnePiece, Language.English, "OP99-999")))
    }

    @Test
    fun remembering_a_printing_again_replaces_its_saved_details() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val original = printing(zoro)

        collection.rememberPrintings(listOf(original))
        collection.rememberPrintings(listOf(original.copy(name = "Renamed")))

        assertEquals("Renamed", collection.lastKnownPrinting(zoro)?.name)
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
    fun search_matches_base_number_or_name_in_one_language_and_treats_wildcards_literally() = runTest {
        val en = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val jp = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(en).copy(name = "Zoro")))
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(printing(jp).copy(name = "Zoro")))

        assertEquals(listOf(en), catalog.search(Game.OnePiece, Language.English, "zor").map { it.identifier })
        assertEquals(listOf(en), catalog.search(Game.OnePiece, Language.English, "op01-0").map { it.identifier })
        assertEquals(emptyList(), catalog.search(Game.OnePiece, Language.English, "%"))
        assertEquals(emptyList(), catalog.search(Game.OnePiece, Language.English, "OP01_001"))
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
    fun an_empty_location_can_be_deleted() = runTest {
        val bulk = collection.createLocation("Bulk box A")

        assertEquals(DeleteLocationResult.Deleted, collection.deleteLocation(bulk.id))
        assertEquals(emptyList(), collection.locations())
    }

    @Test
    fun a_location_holding_an_owned_copy_cannot_be_deleted() = runTest {
        val id = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(id, location = binder.id))

        assertEquals(DeleteLocationResult.NotEmpty, collection.deleteLocation(binder.id))
        assertEquals(listOf(binder), collection.locations())
        assertEquals(1, collection.ownedCopies(id).size)
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
        old.rememberPrintings(listOf(printing(zoro)))
        val replacement = CollectionSnapshot(
            locations = listOf(Location(LocationId(42), "Binder 1")),
            copies = listOf(
                OwnedCopy(OwnedCopyId(7), zoro, Condition.Damaged, LocationId(42), "creased"),
                OwnedCopy(OwnedCopyId(8), zoro, Condition.Damaged, LocationId(42), "creased"),
            ),
        )

        collection.replaceCollection(replacement)

        assertEquals(replacement, collection.snapshot())
        assertNull(collection.lastKnownPrinting(zoro))
    }

    @Test
    fun custom_printings_are_listed_per_language_survive_a_replace_that_carries_them_and_can_be_deleted() = runTest {
        val custom = PrintingIdentifier(Game.OnePiece, Language.English, "custom:1")
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val milk = printing(custom).copy(baseNumber = "OP01-001", releaseProduct = "Custom", name = "Milk Box Stamp")
        collection.rememberPrintings(listOf(milk, printing(zoro)))

        assertEquals(listOf(milk), collection.customPrintings(Game.OnePiece, Language.English))
        assertEquals(emptyList(), collection.customPrintings(Game.OnePiece, Language.Japanese))
        assertEquals(listOf(milk), collection.snapshot().customPrintings)

        collection.replaceCollection(CollectionSnapshot(emptyList(), emptyList(), listOf(milk)))
        assertEquals(listOf(milk), collection.customPrintings(Game.OnePiece, Language.English))
        assertNull(collection.lastKnownPrinting(zoro))

        collection.deleteCustomPrinting(custom)
        assertNull(collection.lastKnownPrinting(custom))
    }

    @Test
    fun a_replace_that_fails_part_way_leaves_the_old_collection_intact() = runTest {
        val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(zoro, location = binder.id, notes = "keep me"))
        collection.rememberPrintings(listOf(printing(zoro)))
        val before = collection.snapshot()
        val broken = CollectionSnapshot(
            locations = emptyList(),
            copies = listOf(OwnedCopy(OwnedCopyId(1), zoro, Condition.NearMint, LocationId(9999), "")),
        )

        val failed = runCatching { collection.replaceCollection(broken) }

        assertTrue(failed.isFailure)
        assertEquals(before, collection.snapshot())
        assertNotNull(collection.lastKnownPrinting(zoro))
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
    )
}
