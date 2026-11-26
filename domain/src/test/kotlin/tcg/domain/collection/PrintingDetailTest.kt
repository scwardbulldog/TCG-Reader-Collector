package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintingDetailTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
    private val detail = PrintingDetail(zoro, collection, catalog)

    @Test
    fun `lists only the Owned Copies of its Printing with the Catalog entry and Locations`() = runTest {
        val entry = Printing(zoro, "OP01-001", "Romance Dawn", "L", "Roronoa Zoro", "https://example.invalid/z.png")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(entry))
        val first = collection.add(NewOwnedCopy(zoro))
        val second = collection.add(NewOwnedCopy(zoro))
        collection.add(NewOwnedCopy(nami))
        val binder = collection.createLocation("Binder 3")

        detail.load()

        val state = detail.state.value
        assertEquals(entry, state.catalogEntry)
        assertEquals(listOf(first, second), state.copies)
        assertEquals(listOf(binder), state.locations)
    }

    @Test
    fun `an edited condition, Location and notes are saved to that Owned Copy only`() = runTest {
        val edited = collection.add(NewOwnedCopy(zoro))
        val untouched = collection.add(NewOwnedCopy(zoro))
        val binder = collection.createLocation("Binder 3")

        detail.load()
        detail.save(edited.copy(condition = Condition.LightlyPlayed, location = binder.id, notes = "corner wear"))

        assertEquals(
            listOf(edited.copy(condition = Condition.LightlyPlayed, location = binder.id, notes = "corner wear"), untouched),
            collection.ownedCopies(zoro),
        )
        assertEquals(collection.ownedCopies(zoro), detail.state.value.copies)
    }

    @Test
    fun `an Owned Copy is deleted only after confirmation`() = runTest {
        val keep = collection.add(NewOwnedCopy(zoro))
        val doomed = collection.add(NewOwnedCopy(zoro))
        detail.load()

        detail.requestDelete(doomed.id)
        assertEquals(doomed.id, detail.state.value.pendingDelete)
        assertEquals(listOf(keep, doomed), collection.ownedCopies(zoro))

        detail.confirmDelete()

        assertEquals(listOf(keep), collection.ownedCopies(zoro))
        assertEquals(listOf(keep), detail.state.value.copies)
        assertNull(detail.state.value.pendingDelete)
    }

    @Test
    fun `cancelling a delete keeps the Owned Copy`() = runTest {
        val copy = collection.add(NewOwnedCopy(zoro))
        detail.load()

        detail.requestDelete(copy.id)
        detail.cancelDelete()
        detail.confirmDelete()

        assertEquals(listOf(copy), collection.ownedCopies(zoro))
        assertNull(detail.state.value.pendingDelete)
    }

    @Test
    fun `a Location created from the detail screen is available to choose`() = runTest {
        detail.load()

        val created = detail.createLocation("Bulk box A")

        assertEquals(listOf(created), detail.state.value.locations)
        assertEquals(listOf(created), collection.locations())
    }
}
