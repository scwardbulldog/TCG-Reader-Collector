package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.catalog.CatalogTarget
import tcg.domain.catalog.CatalogUpdater
import tcg.domain.catalog.FakeCatalogSource
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.catalog.catalogJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Catalog refreshes that drop or restore a Printing the Collection refers to. */
class CatalogGapsTest {
    private val source = FakeCatalogSource()
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val list = CollectionList(collection, catalog)
    private val updater = CatalogUpdater(
        source,
        catalog,
        listOf(CatalogTarget(Game.OnePiece, Language.English)),
        beforeReplace = LastKnownPrintings(collection, catalog)::remember,
    )

    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p1")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")

    private fun publish(version: String, vararg printings: Pair<String, String>) =
        source.publish(Game.OnePiece, Language.English, version, catalogJson("en", *printings))

    private suspend fun refresh(version: String, vararg printings: Pair<String, String>) {
        publish(version, *printings)
        updater.update()
    }

    private suspend fun ownThenDrop(copies: Int = 2) {
        refresh("v1", "OP01-001_p1" to "Roronoa Zoro", "OP01-016" to "Nami")
        repeat(copies) { collection.add(NewOwnedCopy(zoro)) }
        collection.add(NewOwnedCopy(nami))
        refresh("v2", "OP01-016" to "Nami")
    }

    @Test
    fun `a refresh that drops an owned Printing keeps its Owned Copies, last-known details and a flag`() = runTest {
        ownThenDrop()

        val row = list.rows().single { it.printing == zoro }

        assertEquals(2, row.ownedCopyCount)
        assertEquals("Roronoa Zoro", row.name)
        assertEquals("OP01-001", row.baseNumber)
        assertTrue(row.noLongerInCatalog)
        assertEquals(2, collection.ownedCopies(zoro).size)
    }

    @Test
    fun `Printings still in the Catalog are not flagged`() = runTest {
        ownThenDrop()

        assertFalse(list.rows().single { it.printing == nami }.noLongerInCatalog)
    }

    @Test
    fun `the flag disappears when a later refresh restores the Printing`() = runTest {
        ownThenDrop()

        refresh("v3", "OP01-001_p1" to "Roronoa Zoro", "OP01-016" to "Nami")

        assertFalse(list.rows().single { it.printing == zoro }.noLongerInCatalog)
    }

    @Test
    fun `a dropped Printing can be searched and filtered by its last-known details`() = runTest {
        ownThenDrop()

        assertEquals(listOf(zoro), list.rows("zoro").map { it.printing })
        assertEquals(listOf(zoro), list.rows("OP01-001").map { it.printing })
        assertEquals(
            listOf(zoro),
            list.rows(filter = CollectionFilter(releaseProduct = "Romance Dawn", variant = Variant.AlternateArt)).map { it.printing },
        )
        assertEquals(listOf("Romance Dawn"), list.releaseProducts())
    }

    @Test
    fun `a dropped Printing's detail shows last-known details, the flag, and stays editable and deletable`() = runTest {
        ownThenDrop()
        val detail = PrintingDetail(zoro, collection, catalog)

        detail.load()

        val loaded = detail.state.value
        assertEquals("Roronoa Zoro", loaded.details?.name)
        assertTrue(loaded.noLongerInCatalog)
        val (first, second) = loaded.copies
        detail.save(first.copy(notes = "sleeved"))
        detail.requestDelete(second.id)
        detail.confirmDelete()
        assertEquals(listOf(first.copy(notes = "sleeved")), detail.state.value.copies)
        assertEquals(listOf(first.copy(notes = "sleeved")), collection.ownedCopies(zoro))
    }

    @Test
    fun `a refresh never deletes or remaps an Owned Copy`() = runTest {
        ownThenDrop(copies = 3)
        val before = collection.ownedCopies(zoro) + collection.ownedCopies(nami)

        refresh("v3", "OP01-001_p2" to "Roronoa Zoro")

        assertEquals(before, collection.ownedCopies(zoro) + collection.ownedCopies(nami))
        assertEquals(emptyList(), collection.ownedCopies(PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001_p2")))
        assertEquals(setOf(zoro, nami), list.rows().map { it.printing }.toSet())
    }

    @Test
    fun `a failed refresh leaves the Catalog and flags unchanged`() = runTest {
        ownThenDrop()
        source.failWith(Game.OnePiece, Language.English, java.io.IOException("offline"))

        updater.update()

        assertTrue(list.rows().single { it.printing == zoro }.noLongerInCatalog)
        assertFalse(list.rows().single { it.printing == nami }.noLongerInCatalog)
    }

    @Test
    fun `a Printing with no saved details is an Unresolved Printing, not flagged`() = runTest {
        collection.add(NewOwnedCopy(zoro))

        val row = list.rows().single()

        assertEquals(null, row.details)
        assertFalse(row.noLongerInCatalog)
    }
}
