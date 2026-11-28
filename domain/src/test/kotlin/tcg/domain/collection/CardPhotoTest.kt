package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.backup.CollectionBackup
import tcg.domain.backup.InMemoryBackupStatusStore
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.scanning.InMemoryLanguageStore
import tcg.domain.scanning.ScanSession
import tcg.domain.scanning.ScanSessionInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardPhotoTest {
    private val collection = InMemoryCollectionStore()
    private val catalog = InMemoryCatalogStore(collection)
    private val photos = InMemoryCardPhotoStore()
    private var ids = 0
    private val custom = CustomPrintings(collection, photos, newId = { "id${ids++}" })

    private suspend fun create(photo: String? = null) =
        custom.create(Game.OnePiece, Language.English, "OP01-001", "Milk Box Stamp", photo)

    private fun session() =
        ScanSessionInput(ScanSession(Game.OnePiece, InMemoryLanguageStore(Language.English)), catalog, collection, photos)

    @Test
    fun `a Custom Printing created with a photo keeps it, and one created without still works`() = runTest {
        val with = create(photo = "shot-1")
        val without = create()

        assertEquals("shot-1", photos.captured(with.identifier))
        assertNotNull(with.cardPhoto)
        assertEquals(with, collection.customPrinting(with.identifier))
        assertNotNull(photos.resolve(with.cardPhoto!!))
        assertNull(without.cardPhoto)
        assertNull(photos.captured(without.identifier))
    }

    @Test
    fun `a photo that cannot be saved leaves the Custom Printing created without one`() = runTest {
        photos.failing = true

        val printing = create(photo = "shot-1")

        assertNull(printing.cardPhoto)
        assertEquals(printing, collection.customPrinting(printing.identifier))
    }

    @Test
    fun `adding a photo to an existing Custom Printing and retaking it leaves one photo, the latest`() = runTest {
        val printing = create()

        val added = custom.setPhoto(printing.identifier, "shot-1")!!
        val retaken = custom.setPhoto(printing.identifier, "shot-2")!!

        assertEquals("shot-2", photos.captured(printing.identifier))
        assertEquals(1, photos.count)
        assertNotEquals(added.cardPhoto, retaken.cardPhoto)
        assertNull(photos.resolve(added.cardPhoto!!))
        assertNotNull(photos.resolve(retaken.cardPhoto!!))
        assertEquals(retaken, collection.customPrinting(printing.identifier))
    }

    @Test
    fun `a photo that cannot be saved on a retake keeps the old one`() = runTest {
        val printing = create(photo = "shot-1")
        photos.failing = true

        assertFailsWith<java.io.IOException> { custom.setPhoto(printing.identifier, "shot-2") }

        assertEquals("shot-1", photos.captured(printing.identifier))
        assertEquals(printing, collection.customPrinting(printing.identifier))
    }

    @Test
    fun `removing the photo deletes the file and leaves the Printing`() = runTest {
        val printing = create(photo = "shot-1")

        val without = custom.removePhoto(printing.identifier)

        assertNull(without?.cardPhoto)
        assertEquals(0, photos.count)
        assertEquals(without, collection.customPrinting(printing.identifier))
    }

    @Test
    fun `renaming a Custom Printing keeps its photo`() = runTest {
        val printing = create(photo = "shot-1")

        val renamed = custom.rename(printing.identifier, "Renamed")

        assertEquals(printing.cardPhoto, renamed?.cardPhoto)
    }

    @Test
    fun `deleting a Custom Printing deletes its photo, but not while an Owned Copy uses it`() = runTest {
        val printing = create(photo = "shot-1")
        val copy = collection.add(NewOwnedCopy(printing.identifier))

        custom.delete(printing.identifier)
        assertEquals(1, photos.count)

        collection.delete(copy.id)
        custom.delete(printing.identifier)
        assertEquals(0, photos.count)
    }

    @Test
    fun `discarding an unused Custom Printing at scan time deletes its photo`() = runTest {
        val input = session()
        val printing = input.createCustomPrinting("OP01-001", "Milk Box Stamp", photo = "shot-1")
        assertEquals(1, photos.count)

        input.discardCustomPrinting(printing)

        assertEquals(0, photos.count)
        assertNull(collection.customPrinting(printing.identifier))
    }

    @Test
    fun `the Printing detail adds, retakes and removes the photo`() = runTest {
        val printing = create()
        collection.add(NewOwnedCopy(printing.identifier))
        val detail = PrintingDetail(printing.identifier, collection, catalog, photos)
        detail.load()

        detail.setPhoto("shot-1")
        val first = detail.state.value.details?.cardPhoto
        detail.setPhoto("shot-2")
        val second = detail.state.value.details?.cardPhoto

        assertNotNull(first)
        assertNotEquals(first, second)
        assertEquals("shot-2", photos.captured(printing.identifier))

        detail.removePhoto()
        assertNull(detail.state.value.details?.cardPhoto)
        assertEquals(0, photos.count)
    }

    @Test
    fun `Delete both on the detail deletes the photo`() = runTest {
        val printing = create(photo = "shot-1")
        val copy = collection.add(NewOwnedCopy(printing.identifier))
        val detail = PrintingDetail(printing.identifier, collection, catalog, photos)
        detail.load()

        detail.requestDelete(copy.id)
        detail.confirmDelete(alsoDeleteCustomPrinting = true)

        assertEquals(0, photos.count)
    }

    @Test
    fun `moving an Owned Copy off a Custom Printing leaves the Printing and its photo`() = runTest {
        val printing = create(photo = "shot-1")
        val copy = collection.add(NewOwnedCopy(printing.identifier))
        val detail = PrintingDetail(printing.identifier, collection, catalog, photos)
        detail.load()

        detail.move(copy.id, PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001"))

        assertEquals(printing, collection.customPrinting(printing.identifier))
        assertEquals("shot-1", photos.captured(printing.identifier))
    }

    @Test
    fun `a Catalog Printing cannot have a photo`() = runTest {
        val catalogued = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")

        assertNull(custom.setPhoto(catalogued, "shot-1"))
        assertEquals(0, photos.count)
        assertFailsWith<IllegalArgumentException> {
            Printing(catalogued, "OP01-001", "", "", "Roronoa Zoro", "", Variant.Normal, cardPhoto = "x")
        }
    }

    @Test
    fun `a restore leaves no photos behind, since it replaces every Custom Printing`() = runTest {
        create(photo = "shot-1")
        val backup = CollectionBackup(collection, InMemoryBackupStatusStore(), photos)

        backup.restore(backup.validate(backup.prepare().content))

        assertEquals(0, photos.count)
        assertTrue(collection.snapshot().customPrintings.all { it.cardPhoto == null })
    }
}
