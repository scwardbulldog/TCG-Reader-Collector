package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.backup.CollectionBackup
import tcg.domain.backup.BackupFormat
import tcg.domain.backup.BackupFormatException
import tcg.domain.backup.InMemoryBackupStatusStore
import tcg.domain.catalog.CatalogTarget
import tcg.domain.catalog.CatalogUpdater
import tcg.domain.catalog.FakeCatalogSource
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.catalog.catalogJson
import tcg.domain.isCustom
import tcg.domain.scanning.InMemoryLanguageStore
import tcg.domain.scanning.ScanLookup
import tcg.domain.scanning.ScanSession
import tcg.domain.scanning.ScanSessionInput
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomPrintingsTest {
    private val source = FakeCatalogSource()
    private val collection = InMemoryCollectionStore()
    private val catalog = InMemoryCatalogStore(collection)
    private val updater = CatalogUpdater(source, catalog, listOf(CatalogTarget(Game.OnePiece, Language.English)))
    private var ids = 0
    private val photos = InMemoryCardPhotoStore()
    private val custom = CustomPrintings(collection, photos, newId = { "id${ids++}" })

    private fun session(language: Language = Language.English): ScanSessionInput =
        ScanSessionInput(ScanSession(Game.OnePiece, InMemoryLanguageStore(language)), catalog, collection, photos)

    private suspend fun create(base: String = "OP01-001", label: String = "Milk Box Stamp") =
        custom.create(Game.OnePiece, Language.English, base, label)

    @Test
    fun `a Custom Printing has the Collector's label, Custom as Release Product and an id outside Bandai's`() = runTest {
        val printing = create("op01-001", " Milk Box Stamp ")

        assertEquals("OP01-001", printing.baseNumber)
        assertEquals("Milk Box Stamp", printing.name)
        assertEquals("Custom", printing.releaseProduct)
        assertEquals("", printing.imageUrl)
        assertEquals("", printing.rarity)
        assertNull(printing.variant)
        assertEquals(PrintingIdentifier(Game.OnePiece, Language.English, "custom:id0"), printing.identifier)
        assertTrue(printing.identifier.isCustom)
        assertFailsWith<IllegalArgumentException> { create(label = "  ") }
        assertFailsWith<IllegalArgumentException> { create(base = " ") }
    }

    @Test
    fun `a no-match becomes a candidate once a Custom Printing exists, in that Language only`() = runTest {
        val input = session()
        assertTrue(input.lookup("OP01-001") is ScanLookup.NoMatch)

        val printing = input.createCustomPrinting("OP01-001", "Milk Box Stamp")

        val found = input.lookup("op01-001") as ScanLookup.Candidates
        assertEquals(listOf(printing), found.printings)
        assertEquals(printing, found.preselected)
        assertTrue((session(Language.Japanese).lookup("OP01-001")) is ScanLookup.NoMatch)
    }

    @Test
    fun `a Custom Printing is proposed after the Catalog's Printings of its Base Number and its label is not searched`() = runTest {
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001" to "Roronoa Zoro", "OP01-001_p1" to "Roronoa Zoro"))
        updater.update()
        val input = session()
        val printing = input.createCustomPrinting("OP01-001", "Milk Box Stamp")

        val candidates = (input.lookup("OP01-001") as ScanLookup.Candidates).printings

        assertEquals(listOf("OP01-001", "OP01-001_p1", printing.identifier.sourceId), candidates.map { it.identifier.sourceId })
        assertTrue(input.searchCards("milk box").isEmpty())
    }

    @Test
    fun `an Owned Copy of a Custom Printing survives a Catalog refresh and is never flagged`() = runTest {
        val printing = create()
        collection.add(NewOwnedCopy(printing.identifier))
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001" to "Roronoa Zoro"))
        updater.update()
        source.publish(Game.OnePiece, Language.English, "v2", catalogJson("en", "OP01-016" to "Nami"))
        updater.update()

        val row = CollectionList(collection, catalog).rows().single()

        assertEquals(printing, row.details)
        assertFalse(row.noLongerInCatalog)
        assertFalse(row.unresolved)
        assertEquals(listOf("Custom"), CollectionList(collection, catalog).releaseProducts().map { it.releaseProduct })
    }

    @Test
    fun `the CSV export gives a Custom Printing its details and no Variant`() = runTest {
        val printing = create()
        collection.add(NewOwnedCopy(printing.identifier))

        val lines = CollectionExport(collection, catalog).csv().lines()

        assertEquals(
            "onepiece,EN,OP01-001,onepiece / EN / custom:id0,Milk Box Stamp,,,Custom,Near Mint,,",
            lines[1],
        )
    }

    @Test
    fun `a Variant filter excludes a Custom Printing, which has no Variant`() = runTest {
        source.publish(Game.OnePiece, Language.English, "v1", catalogJson("en", "OP01-001" to "Roronoa Zoro"))
        updater.update()
        val catalogued = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
        val printing = create()
        collection.add(NewOwnedCopy(catalogued))
        collection.add(NewOwnedCopy(printing.identifier))

        val rows = CollectionList(collection, catalog).rows(filter = CollectionFilter(variant = tcg.domain.Variant.Normal))

        assertEquals(listOf(catalogued), rows.map { it.printing })
    }

    @Test
    fun `renaming changes the label for every Owned Copy of the Printing`() = runTest {
        val printing = create()
        collection.add(NewOwnedCopy(printing.identifier))
        collection.add(NewOwnedCopy(printing.identifier))

        custom.rename(printing.identifier, "Milk Box Stamp 2024")

        assertEquals("Milk Box Stamp 2024", CollectionList(collection, catalog).rows().single().name)
        assertNull(custom.rename(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001"), "Nope"))
    }

    @Test
    fun `a Custom Printing is deleted only while no Owned Copy uses it`() = runTest {
        val printing = create()
        val copy = collection.add(NewOwnedCopy(printing.identifier))

        assertEquals(DeleteCustomPrintingResult.InUse, custom.delete(printing.identifier))
        assertNotNull(collection.customPrinting(printing.identifier))

        collection.delete(copy.id)
        assertEquals(DeleteCustomPrintingResult.Deleted, custom.delete(printing.identifier))
        assertNull(collection.customPrinting(printing.identifier))
        assertEquals(DeleteCustomPrintingResult.NotFound, custom.delete(printing.identifier))
        assertEquals(
            DeleteCustomPrintingResult.NotFound,
            custom.delete(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")),
        )
    }

    @Test
    fun `the Printing detail renames a Custom Printing and can delete it with its last copy`() = runTest {
        val printing = create()
        val copy = collection.add(NewOwnedCopy(printing.identifier))
        val detail = PrintingDetail(printing.identifier, collection, catalog, photos)
        detail.load()

        detail.rename("Renamed")
        assertEquals("Renamed", detail.state.value.details?.name)
        assertEquals(1, detail.state.value.totalCopies)

        detail.requestDelete(copy.id)
        detail.confirmDelete(alsoDeleteCustomPrinting = true)

        assertTrue(detail.state.value.copies.isEmpty())
        assertNull(collection.customPrinting(printing.identifier))
    }

    @Test
    fun `a backup round trip restores a Custom Printing as itself, not an Unresolved Printing`() = runTest {
        val printing = create()
        collection.add(NewOwnedCopy(printing.identifier))
        val backup = CollectionBackup(collection, InMemoryBackupStatusStore(), photos, clock = { Instant.parse("2026-11-27T12:00:00Z") })
        val content = backup.prepare().content
        val fresh = InMemoryCollectionStore()

        val restorer = CollectionBackup(fresh, InMemoryBackupStatusStore(), photos)
        restorer.restore(restorer.validate(content))

        val row = CollectionList(fresh, catalog).rows().single()
        assertEquals(printing, row.details)
        assertFalse(row.unresolved)
        assertFalse(row.noLongerInCatalog)
        assertEquals(listOf(printing), CustomPrintings(fresh, photos).forBaseNumber(Game.OnePiece, Language.English, "OP01-001"))
    }

    @Test
    fun `a backup without Custom Printings still reads, and a malformed one is rejected`() = runTest {
        val old = """{"format":"tcg-collection-backup","schemaVersion":1,"createdAt":"2026-11-27T12:00:00Z","locations":[],"ownedCopies":[]}"""
        assertEquals(emptyList<Printing>(), BackupFormat.parse(old).snapshot.customPrintings)

        val bad = old.replace("\"ownedCopies\":[]", "\"ownedCopies\":[],\"customPrintings\":[{\"game\":\"onepiece\",\"language\":\"en\",\"sourceId\":\"OP01-001_p1\",\"baseNumber\":\"OP01-001\",\"label\":\"x\"}]")
        assertFailsWith<BackupFormatException> { BackupFormat.parse(bad) }
    }
}
