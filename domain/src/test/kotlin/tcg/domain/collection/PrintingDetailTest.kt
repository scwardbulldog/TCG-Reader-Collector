package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.catalog.InMemoryCatalogStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintingDetailTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
    private val photos = InMemoryCardPhotoStore()
    private val detail = PrintingDetail(zoro, collection, catalog, photos)

    @Test
    fun `lists only the Owned Copies of its Printing with the Catalog entry and Locations`() = runTest {
        val entry = Printing(zoro, "OP01-001", "Romance Dawn", "L", "Roronoa Zoro", "https://example.invalid/z.png", Variant.Normal)
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(entry))
        val first = collection.add(NewOwnedCopy(zoro))
        val second = collection.add(NewOwnedCopy(zoro))
        collection.add(NewOwnedCopy(nami))
        val binder = collection.createLocation("Binder 3")

        detail.load()

        val state = detail.state.value
        assertEquals(entry, state.details)
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
    fun `the detail is not loaded until load finishes so an empty list can mean the last copy was deleted`() = runTest {
        val only = collection.add(NewOwnedCopy(zoro))
        assertEquals(false, detail.state.value.loaded)

        detail.load()
        assertEquals(true, detail.state.value.loaded)

        detail.requestDelete(only.id)
        detail.confirmDelete()

        assertEquals(true, detail.state.value.loaded)
        assertEquals(emptyList(), detail.state.value.copies)
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

    @Test
    fun `a Location filter shows only the Owned Copies kept there`() = runTest {
        val binder = collection.createLocation("Binder 1")
        val bulk = collection.createLocation("Bulk box A")
        val a = collection.add(NewOwnedCopy(zoro, location = binder.id))
        val b = collection.add(NewOwnedCopy(zoro, location = binder.id))
        collection.add(NewOwnedCopy(zoro, location = bulk.id))
        val filtered = PrintingDetail(zoro, collection, catalog, photos, location = binder.id)

        filtered.load()

        assertEquals(listOf(a, b), filtered.state.value.copies)
    }

    @Test
    fun `Add another copy copies the last copy's condition and Location but not its notes`() = runTest {
        val binder = collection.createLocation("Binder 1")
        collection.add(NewOwnedCopy(zoro))
        collection.add(NewOwnedCopy(zoro, Condition.Damaged, binder.id, "creased"))
                val filtered = PrintingDetail(zoro, collection, catalog, photos, location = binder.id)
        filtered.load()

        filtered.addAnotherCopy()

        val added = filtered.state.value.copies.last()
        assertEquals(Triple(Condition.Damaged, binder.id, ""), Triple(added.condition, added.location, added.notes))
        assertEquals(2, filtered.state.value.copies.size)
        assertEquals(3, filtered.state.value.totalCopies)
    }

    @Test
    fun `Add another copy records when the copy was added`() = runTest {
        val at = java.time.Instant.parse("2026-11-27T12:00:00Z")
        val clocked = InMemoryCollectionStore { at }
        val d = PrintingDetail(zoro, clocked, catalog, photos)
        d.load()

        d.addAnotherCopy()

        assertEquals(at, d.state.value.copies.single().addedAt)
    }

    @Test
    fun `an Owned Copy moved out of the filtered Location stays visible while it is edited`() = runTest {
        val binder = collection.createLocation("Binder 1")
        val bulk = collection.createLocation("Bulk box A")
        val moved = collection.add(NewOwnedCopy(zoro, location = binder.id))
        val filtered = PrintingDetail(zoro, collection, catalog, photos, location = binder.id)
        filtered.load()

        filtered.save(moved.copy(location = bulk.id))

        assertEquals(listOf(moved.copy(location = bulk.id)), filtered.state.value.copies)
    }

    @Test
    fun `moving one Owned Copy to another Printing keeps its condition, Location and notes and leaves other copies alone`() = runTest {
        val binder = collection.createLocation("Binder 1")
        val moved = collection.add(NewOwnedCopy(zoro, Condition.Damaged, binder.id, "bent"))
        val stays = collection.add(NewOwnedCopy(zoro))
        val other = collection.add(NewOwnedCopy(nami))
        detail.load()

        detail.move(moved.id, nami)

        assertEquals(listOf(stays), collection.ownedCopies(zoro))
        assertEquals(listOf(moved.copy(printing = nami), other), collection.ownedCopies(nami))
    }

    @Test
    fun `a copy on a flagged Printing can be moved to a Printing in another Language`() = runTest {
        val japanese = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001")
        val copy = collection.add(NewOwnedCopy(zoro))
        detail.load()

        detail.move(copy.id, japanese)

        assertEquals(listOf(copy.copy(printing = japanese)), collection.ownedCopies(japanese))
        assertEquals(emptyList(), collection.ownedCopies(zoro))
    }

    @Test
    fun `a copy can be moved to a new Custom Printing in the same Language`() = runTest {
        val copy = collection.add(NewOwnedCopy(zoro, notes = "promo"))
        val other = collection.add(NewOwnedCopy(zoro))
        detail.load()

        val target = detail.moveToNewCustom(copy.id, "op01-001", "Milk Box Stamp")

        assertEquals(Language.English, target.language)
        assertEquals("Milk Box Stamp", collection.customPrinting(target)?.name)
        assertEquals(listOf(copy.copy(printing = target)), collection.ownedCopies(target))
        assertEquals(listOf(other), collection.ownedCopies(zoro))
    }

    @Test
    fun `saving a stale copy does not move it back to its old Printing`() = runTest {
        val copy = collection.add(NewOwnedCopy(zoro))
        detail.load()
        detail.move(copy.id, nami)

        detail.save(copy.copy(notes = "late edit"))

        assertEquals(listOf(copy.copy(printing = nami, notes = "late edit")), collection.ownedCopies(nami))
    }}
