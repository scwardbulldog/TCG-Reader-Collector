package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.backup.CollectionBackup
import tcg.domain.backup.InMemoryBackupStatusStore
import tcg.domain.backup.backupFile
import tcg.domain.backup.restoreFile
import tcg.domain.catalog.CatalogTarget
import tcg.domain.catalog.CatalogUpdater
import tcg.domain.catalog.FakeCatalogSource
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.catalog.catalogJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Collection restored from a backup before the Catalog knows its Printings. */
class UnresolvedPrintingTest {
    private val source = FakeCatalogSource()
    private val collection = InMemoryCollectionStore()
    private val catalog = InMemoryCatalogStore(collection)
    private val list = CollectionList(collection, catalog)
    private val updater = CatalogUpdater(source, catalog, listOf(CatalogTarget(Game.OnePiece, Language.English)))

    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
    private val luffy = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-003")

    /** Backs up a Collection on another "phone" and restores it here, where the Catalog is still empty. */
    private suspend fun restoreOnFreshInstall() {
        val other = InMemoryCollectionStore()
        val binder = other.createLocation("Binder 1")
        repeat(2) { other.add(NewOwnedCopy(zoro, location = binder.id)) }
        other.add(NewOwnedCopy(nami))
        other.add(NewOwnedCopy(luffy))
        val content = CollectionBackup(other, InMemoryBackupStatusStore(), InMemoryCardPhotoStore()).backupFile()
        val restore = CollectionBackup(collection, InMemoryBackupStatusStore(), InMemoryCardPhotoStore())
        restore.restoreFile(content)
    }

    private suspend fun publishCatalog() {
        source.publish(
            Game.OnePiece, Language.English, "v1",
            catalogJson("en", "OP01-001_p1" to "Roronoa Zoro", "OP01-016" to "Nami"),
        )
        updater.update()
    }

    @Test
    fun `restored Owned Copies whose Printing the Catalog lacks stay in the list with counts, flagged Unresolved`() = runTest {
        restoreOnFreshInstall()

        val rows = list.rows()

        assertEquals(setOf(zoro, nami, luffy), rows.map { it.printing }.toSet())
        assertEquals(2, rows.single { it.printing == zoro }.ownedCopyCount)
        assertTrue(rows.all { it.unresolved })
    }

    @Test
    fun `an Unresolved Printing shows no invented name, Base Number, Release Product or Variant`() = runTest {
        restoreOnFreshInstall()

        val row = list.rows().single { it.printing == zoro }

        assertNull(row.name)
        assertNull(row.baseNumber)
        assertNull(row.details)
        assertFalse(row.noLongerInCatalog)
        assertEquals(emptyList(), list.releaseProducts())
    }

    @Test
    fun `Unresolved Printings match Language and Location filters but not Release Product or Variant filters`() = runTest {
        restoreOnFreshInstall()
        val binder = collection.locations().single().id

        assertEquals(setOf(zoro, nami), list.rows(filter = CollectionFilter(language = Language.English)).map { it.printing }.toSet())
        assertEquals(listOf(zoro), list.rows(filter = CollectionFilter(location = binder)).map { it.printing })
        assertEquals(emptyList(), list.rows(filter = CollectionFilter(variant = Variant.Normal)))
        assertEquals(emptyList(), list.rows(filter = CollectionFilter(releaseProduct = "Romance Dawn")))
    }

    @Test
    fun `an Unresolved Printing is found by its identifier`() = runTest {
        restoreOnFreshInstall()

        assertEquals(listOf(nami), list.rows("op01-016").map { it.printing })
    }

    @Test
    fun `an Unresolved Printing's detail is flagged and its Owned Copies stay editable and deletable`() = runTest {
        restoreOnFreshInstall()
        val detail = PrintingDetail(zoro, collection, catalog, InMemoryCardPhotoStore())

        detail.load()

        assertTrue(detail.state.value.unresolved)
        val (first, second) = detail.state.value.copies
        detail.save(first.copy(notes = "sleeved"))
        detail.requestDelete(second.id)
        detail.confirmDelete()
        assertEquals(listOf(first.copy(notes = "sleeved")), collection.ownedCopies(zoro))
    }

    @Test
    fun `a later Catalog update that includes the Printing resolves it, keeping the Owned Copies`() = runTest {
        restoreOnFreshInstall()
        val before = collection.snapshot()

        publishCatalog()

        val rows = list.rows()
        val resolved = rows.single { it.printing == zoro }
        assertFalse(resolved.unresolved)
        assertFalse(resolved.noLongerInCatalog)
        assertEquals("Roronoa Zoro", resolved.name)
        assertEquals(2, resolved.ownedCopyCount)
        assertTrue(rows.single { it.printing == luffy }.unresolved)
        assertEquals(before, collection.snapshot())
    }
}
