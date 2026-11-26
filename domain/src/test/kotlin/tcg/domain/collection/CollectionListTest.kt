package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionListTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val list = CollectionList(collection, catalog)
    private val catalogued = mutableMapOf<Language, List<Printing>>()

    private fun id(language: Language, sourceId: String) = PrintingIdentifier(Game.OnePiece, language, sourceId)

    /** Adds a Printing to the Catalog and gives the collector [copies] Owned Copies of it. */
    private suspend fun own(language: Language, sourceId: String, baseNumber: String, name: String, copies: Int = 1): PrintingIdentifier {
        val identifier = id(language, sourceId)
        catalogued[language] = catalogued[language].orEmpty() +
            Printing(identifier, baseNumber, "Romance Dawn", "L", name, "https://example.invalid/$sourceId.png")
        catalog.replaceCatalog(Game.OnePiece, language, catalogued.getValue(language))
        repeat(copies) { collection.add(NewOwnedCopy(identifier)) }
        return identifier
    }

    /** Owned Copies of a Printing the Catalog has no record of. */
    private suspend fun ownUnresolved(language: Language, sourceId: String, copies: Int = 1): PrintingIdentifier {
        val identifier = id(language, sourceId)
        repeat(copies) { collection.add(NewOwnedCopy(identifier)) }
        return identifier
    }

    @Test
    fun `an empty Collection has no rows`() = runTest {
        assertEquals(emptyList(), list.rows())
    }

    @Test
    fun `each Printing is one row with its derived Owned Copy count`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", copies = 3)
        val nami = own(Language.English, "OP01-016", "OP01-016", "Nami")

        assertEquals(listOf(zoro to 3, nami to 1), list.rows().map { it.printing to it.ownedCopyCount })
    }

    @Test
    fun `the same Base Number in two Languages or Variants stays separate rows`() = runTest {
        val en = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val jp = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ", copies = 2)
        val alt = own(Language.English, "OP01-001_p1", "OP01-001", "Roronoa Zoro")
        val reprint = own(Language.English, "OP01-001_r1", "OP01-001", "Roronoa Zoro")

        assertEquals(setOf(en, jp, alt, reprint), list.rows().map { it.printing }.toSet())
        assertEquals(4, list.rows().size)
    }

    @Test
    fun `search is case-insensitive and partial on Base Number or name`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val nami = own(Language.English, "OP01-016", "OP01-016", "Nami")

        assertEquals(listOf(zoro), list.rows("zoro").map { it.printing })
        assertEquals(listOf(zoro), list.rows("RONOA").map { it.printing })
        assertEquals(listOf(nami), list.rows("op01-01").map { it.printing })
        assertEquals(listOf(zoro, nami), list.rows("  ").map { it.printing })
        assertEquals(emptyList(), list.rows("luffy").map { it.printing })
    }

    @Test
    fun `an Unresolved Printing matches search on its Printing identifier`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val unresolved = ownUnresolved(Language.English, "ST99-007_p2")

        assertEquals(listOf(unresolved), list.rows("st99-007").map { it.printing })
        assertEquals(listOf(unresolved), list.rows("_P2").map { it.printing })
    }

    @Test
    fun `rows are ordered by Base Number then Language then Printing identifier with no Base Number last`() = runTest {
        val unresolvedEn = ownUnresolved(Language.English, "AA00-001")
        val op02 = own(Language.English, "OP02-013", "OP02-013", "Portgas.D.Ace")
        val op01Jp = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
        val op01EnAlt = own(Language.English, "OP01-001_p1", "OP01-001", "Roronoa Zoro")
        val op01En = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val unresolvedJp = ownUnresolved(Language.Japanese, "AA00-001")

        assertEquals(
            listOf(op01En, op01EnAlt, op01Jp, op02, unresolvedEn, unresolvedJp),
            list.rows().map { it.printing },
        )
    }
}
