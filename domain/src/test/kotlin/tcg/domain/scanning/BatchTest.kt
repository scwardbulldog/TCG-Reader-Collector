package tcg.domain.scanning

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.fixtureVariant
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.collection.InMemoryCollectionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BatchTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()

    private fun printing(sourceId: String, baseNumber: String = sourceId.substringBefore('_')) =
        Printing(PrintingIdentifier(Game.OnePiece, Language.English, sourceId), baseNumber, "Product", "L", "Card $sourceId", "", fixtureVariant(sourceId))

    private suspend fun batch(vararg printings: Printing): Batch {
        catalog.replaceCatalog(Game.OnePiece, Language.English, printings.toList())
        val session = ScanSession(Game.OnePiece, InMemoryLanguageStore())
        return Batch(ScanSessionInput(session, catalog, collection, InMemoryCardPhotoStore()))
    }

    @Test
    fun `reads of the same single-match Printing merge into one resolved row with the counts summed`() = runTest {
        val zoro = printing("OP01-001")
        val batch = batch(zoro)

        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "op01-001")

        val row = assertIs<BatchRow.Resolved>(batch.rows.single())
        assertEquals(zoro, row.printing)
        assertEquals(2, row.count)
    }

    @Test
    fun `a Base Number with several Printings is one ambiguous row preselecting the Normal Printing`() = runTest {
        val normal = printing("OP01-001")
        val altArt = printing("OP01-001_p1")
        val batch = batch(altArt, normal)

        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "OP01-001")

        val row = assertIs<BatchRow.Ambiguous>(batch.rows.single())
        assertEquals(listOf(normal, altArt), row.candidates)
        assertEquals(normal, row.preselected)
        assertEquals(2, row.count)
    }

    @Test
    fun `failed and no-match reads keep their photo and sit above the other rows`() = runTest {
        val zoro = printing("OP01-001")
        val batch = batch(zoro)

        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), null)
        batch.read(PhotoId(3), "OP99-999")
        batch.read(PhotoId(4), "OP99-999")

        val rows = batch.rows
        assertEquals(
            listOf(PhotoId(2) to null, PhotoId(3) to "OP99-999", PhotoId(4) to "OP99-999"),
            rows.take(3).map { assertIs<BatchRow.Failed>(it).let { row -> row.photo to row.baseNumber } },
        )
        assertEquals(zoro, assertIs<BatchRow.Resolved>(rows[3]).printing)
    }

    @Test
    fun `rows list failed first, then ambiguous, then resolved, each in the order they appeared`() = runTest {
        val batch = batch(printing("OP01-001"), printing("OP02-001"), printing("OP02-001_p1"), printing("OP03-001"), printing("OP03-001_p1"))

        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "OP02-001")
        batch.read(PhotoId(3), null)
        batch.read(PhotoId(4), "OP03-001")

        assertEquals(
            listOf("Failed", "Ambiguous", "Ambiguous", "Resolved"),
            batch.rows.map { it::class.simpleName },
        )
        assertEquals(
            listOf("OP02-001", "OP03-001"),
            batch.rows.filterIsInstance<BatchRow.Ambiguous>().map { it.preselected.baseNumber },
        )
    }

    @Test
    fun `picking a Printing resolves an ambiguous row and merges it into a row already on that Printing`() = runTest {
        val normal = printing("OP01-001")
        val altArt = printing("OP01-001_p1")
        val batch = batch(normal, altArt)
        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "OP01-001")
        batch.pick(batch.rows.single().id, normal)
        batch.read(PhotoId(3), "OP01-001")

        batch.pick(batch.rows.filterIsInstance<BatchRow.Ambiguous>().single().id, normal)

        val row = assertIs<BatchRow.Resolved>(batch.rows.single())
        assertEquals(normal, row.printing)
        assertEquals(3, row.count)
    }

    @Test
    fun `only a Printing of the row's own Base Number can be picked`() = runTest {
        val normal = printing("OP01-001")
        val other = printing("OP02-001")
        val batch = batch(normal, printing("OP01-001_p1"), other)
        batch.read(PhotoId(1), "OP01-001")

        assertFailsWith<IllegalArgumentException> { batch.pick(batch.rows.single().id, other) }
    }

    @Test
    fun `fixing a failed row by name offers the matching Cards, and choosing one proposes its Printings`() = runTest {
        val batch = batch(printing("OP01-001").copy(name = "Roronoa Zoro"), printing("OP01-001_p1").copy(name = "Roronoa Zoro"), printing("OP02-001").copy(name = "Nami"))
        batch.read(PhotoId(1), null)
        val failed = batch.rows.single().id

        val matches = batch.fix(failed, "zoro")

        assertEquals(listOf("OP01-001"), matches.map { it.baseNumber })
        assertEquals("zoro", assertIs<BatchRow.Failed>(batch.rows.single()).baseNumber)

        batch.enterBaseNumber(failed, matches.single().baseNumber)

        assertEquals(listOf("OP01-001", "OP01-001_p1"), assertIs<BatchRow.Ambiguous>(batch.rows.single()).candidates.map { it.identifier.sourceId })
    }

    @Test
    fun `fixing a failed row with a Base Number resolves it directly, and a text that matches nothing leaves it failed`() = runTest {
        val batch = batch(printing("OP01-001").copy(name = "Roronoa Zoro"))
        batch.read(PhotoId(1), null)
        batch.read(PhotoId(2), null)

        assertEquals(emptyList(), batch.fix(batch.rows.first().id, "op01-001"))
        assertEquals(emptyList(), batch.fix(batch.rows.first().id, "luffy"))

        assertEquals(listOf("Failed", "Resolved"), batch.rows.map { it::class.simpleName })
        assertEquals("luffy", assertIs<BatchRow.Failed>(batch.rows.first()).baseNumber)
    }

    @Test
    fun `entering a Base Number for a failed row resolves it, or leaves it failed when the Catalog has no match`() = runTest {
        val zoro = printing("OP01-001")
        val batch = batch(zoro)
        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), null)

        batch.enterBaseNumber(batch.rows.first().id, "OP99-999")
        assertEquals("OP99-999", assertIs<BatchRow.Failed>(batch.rows.first()).baseNumber)

        batch.enterBaseNumber(batch.rows.first().id, " op01-001 ")

        val row = assertIs<BatchRow.Resolved>(batch.rows.single())
        assertEquals(2, row.count)
    }

    @Test
    fun `entering a Base Number with several Printings turns a failed row ambiguous`() = runTest {
        val batch = batch(printing("OP01-001"), printing("OP01-001_p1"))
        batch.read(PhotoId(1), null)

        batch.enterBaseNumber(batch.rows.single().id, "OP01-001")

        assertIs<BatchRow.Ambiguous>(batch.rows.single())
    }

    @Test
    fun `removing a row drops it and its whole count`() = runTest {
        val batch = batch(printing("OP01-001"), printing("OP02-001"))
        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "OP01-001")
        batch.read(PhotoId(3), "OP02-001")

        batch.remove(batch.rows.first().id)

        assertEquals("OP02-001", assertIs<BatchRow.Resolved>(batch.rows.single()).printing.baseNumber)
    }

    @Test
    fun `Add all adds only the resolved rows in the given Location at Near Mint and leaves the rest for the collector`() = runTest {
        val zoro = printing("OP01-001")
        val nami = printing("OP01-016")
        val batch = batch(zoro, nami, printing("OP02-001"), printing("OP02-001_p1"))
        val binder = collection.createLocation("Binder 1")
        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "OP01-001")
        batch.read(PhotoId(3), "OP01-016")
        batch.read(PhotoId(4), "OP02-001")
        batch.read(PhotoId(5), null)

        val added = batch.addAll(binder)

        assertEquals(3, added.size)
        assertEquals(2, collection.ownedCopies(zoro.identifier).size)
        assertEquals(1, collection.ownedCopies(nami.identifier).size)
        assertEquals(setOf(Condition.NearMint), added.map { it.condition }.toSet())
        assertEquals(setOf(binder.id), added.map { it.location }.toSet())
        assertEquals(3, collection.countsByPrinting().values.sum())
        assertEquals(listOf("Failed", "Ambiguous"), batch.rows.map { it::class.simpleName })
    }
}
