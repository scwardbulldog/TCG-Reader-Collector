package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.fixtureVariant
import tcg.domain.catalog.InMemoryCatalogStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionExportTest {
    private val collection = InMemoryCollectionStore()
    private val catalog = InMemoryCatalogStore(collection)

    private val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
    private val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
    private val unknown = PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP05-007")

    private fun printing(id: PrintingIdentifier, baseNumber: String, name: String) =
        Printing(id, baseNumber, "Romance Dawn", "L", name, "", fixtureVariant(id.sourceId))

    @Test
    fun `exports every Owned Copy with Location names, last-known details and blanks for Unresolved Printings`() = runTest {
        catalog.replaceCatalog(
            Game.OnePiece, Language.English,
            listOf(printing(zoro, "OP01-001", "Roronoa Zoro"), printing(nami, "OP01-016", "Nami")),
        )
        val binder = collection.createLocation("Binder A")
        collection.add(NewOwnedCopy(zoro, Condition.NearMint, binder.id, "first"))
        collection.add(NewOwnedCopy(zoro, Condition.Damaged, null, "second"))
        collection.add(NewOwnedCopy(nami))
        collection.add(NewOwnedCopy(unknown))
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(printing(zoro, "OP01-001", "Roronoa Zoro")))

        val rows = CollectionExport(collection, catalog).csv().lines().filter { it.isNotEmpty() }.drop(1)

        assertEquals(
            listOf(
                "onepiece,EN,OP01-001,onepiece / EN / OP01-001,Roronoa Zoro,Normal,Romance Dawn,Near Mint,Binder A,first",
                "onepiece,EN,OP01-001,onepiece / EN / OP01-001,Roronoa Zoro,Normal,Romance Dawn,Damaged,,second",
                "onepiece,EN,OP01-016,onepiece / EN / OP01-016,Nami,Normal,Romance Dawn,Near Mint,,",
                "onepiece,JP,,onepiece / JP / OP05-007,,,,Near Mint,,",
            ),
            rows,
        )
    }
}
