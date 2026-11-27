package tcg.domain.scanning

import kotlinx.coroutines.test.runTest
import tcg.domain.CatalogStore
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.fixtureVariant
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.collection.InMemoryCollectionStore
import tcg.domain.collection.PrintingTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanSessionInputTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()

    private fun printing(
        sourceId: String,
        name: String = "Roronoa Zoro",
        baseNumber: String = "OP01-001",
        language: Language = Language.English,
    ) = Printing(PrintingIdentifier(Game.OnePiece, language, sourceId), baseNumber, "Product $sourceId", "L", name, "", fixtureVariant(sourceId))

    private suspend fun seed(language: Language, vararg printings: Printing) =
        catalog.replaceCatalog(Game.OnePiece, language, printings.toList())

    private suspend fun entry(
        language: Language = Language.English,
        catalog: CatalogStore = this.catalog,
    ): Pair<ScanSessionInput, Location> {
        val binder = collection.createLocation("Binder 1")
        val session = ScanSession(Game.OnePiece, InMemoryLanguageStore(language))
        return ScanSessionInput(session, catalog, collection) to binder
    }

    @Test
    fun `a single match is preselected but nothing is added until the collector confirms`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry) = entry()

        val lookup = entry.lookup("OP01-001")

        assertEquals(ScanLookup.Candidates(listOf(zoro), preselected = zoro), lookup)
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `a typed Base Number matches whatever its case or surrounding spaces`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry) = entry()

        assertEquals(ScanLookup.Candidates(listOf(zoro), preselected = zoro), entry.lookup("  op01-001 "))
        assertEquals(ScanLookup.NoMatch("op99-999"), entry.lookup(" op99-999 "))
    }

    @Test
    fun `several matches are listed Normal first with nothing preselected or added`() = runTest {
        val normal = printing("OP01-001")
        val altArt = printing("OP01-001_p1")
        val reprint = printing("OP01-001_r1")
        seed(Language.English, altArt, reprint, normal)
        val (entry) = entry()

        val lookup = assertIs<ScanLookup.Candidates>(entry.lookup("OP01-001"))

        assertEquals(listOf(normal, reprint, altArt), lookup.printings)
        assertNull(lookup.preselected)
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `a Base Number only the other Language has is no match`() = runTest {
        seed(Language.Japanese, printing("OP01-001", language = Language.Japanese))
        val (entry) = entry(Language.English)

        assertEquals(ScanLookup.NoMatch("OP01-001"), entry.lookup("OP01-001"))
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `confirming adds one Owned Copy in the given Location, Near Mint by default`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry, binder) = entry()

        val added = entry.add(zoro, location = binder)

        assertEquals(1, added.size)
        assertEquals(zoro.identifier, added.single().printing)
        assertEquals(binder.id, added.single().location)
        assertEquals(Condition.NearMint, added.single().condition)
        assertEquals(added, collection.ownedCopies(zoro.identifier))
    }

    @Test
    fun `a bulk add of N creates N separate Owned Copies sharing Printing, Location and condition`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry, binder) = entry()

        val added = entry.add(zoro, count = 10, location = binder)

        assertEquals(10, added.size)
        assertEquals(10, added.map { it.id }.toSet().size)
        assertTrue(added.all { it.printing == zoro.identifier && it.location == binder.id && it.condition == Condition.NearMint })
        assertEquals(mapOf(zoro.identifier to 10), collection.countsByPrinting())
    }

    @Test
    fun `a condition override applies to one add only and the next add is Near Mint again`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry) = entry()

        val overridden = entry.add(zoro, condition = Condition.Damaged)
        val next = entry.add(zoro)

        assertEquals(Condition.Damaged, overridden.single().condition)
        assertEquals(Condition.NearMint, next.single().condition)
    }

    @Test
    fun `a Printing of another Language cannot be added to the session`() = runTest {
        val japanese = printing("OP01-001", language = Language.Japanese)
        val (entry) = entry(Language.English)

        assertFailsWith<IllegalArgumentException> { entry.add(japanese) }
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `adding fewer than one copy is rejected`() = runTest {
        val zoro = printing("OP01-001")
        val (entry) = entry()

        assertFailsWith<IllegalArgumentException> { entry.add(zoro, count = 0) }
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `a name search lists each matching Card once in natural Base Number order`() = runTest {
        seed(
            Language.English,
            printing("OP10-113", name = "Roronoa Zoro", baseNumber = "OP10-113"),
            printing("P-004", name = "Zoro Promo", baseNumber = "P-004"),
            printing("OP01-001_p1"),
            printing("OP01-001"),
            printing("OP01-001_r1"),
            printing("OP02-016", name = "Nami", baseNumber = "OP02-016"),
            printing("OP9-001", name = "Zoro Early", baseNumber = "OP9-001"),
        )
        val (entry) = entry()

        val found = entry.searchCards("zoro")

        assertEquals(listOf("OP01-001", "OP9-001", "OP10-113", "P-004"), found.map { it.baseNumber })
        assertEquals(3, found.first().printingCount)
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `a Card row shows the English title with Japanese subtext, the Language's Printing count and the Normal Printing's image`() = runTest {
        fun withImage(printing: Printing, image: String) = printing.copy(imageUrl = image)
        seed(Language.English, printing("OP01-001", name = "Roronoa Zoro"))
        seed(
            Language.Japanese,
            withImage(printing("OP01-001_p1", name = "ロロノア・ゾロ", language = Language.Japanese), "alt.png"),
            withImage(printing("OP01-001", name = "ロロノア・ゾロ", language = Language.Japanese), "normal.png"),
        )
        val (japanese) = entry(Language.Japanese)
        val (english) = entry(Language.English)

        val jp = japanese.searchCards("ゾロ").single()
        assertEquals(PrintingTitle("Roronoa Zoro", "ロロノア・ゾロ"), jp.title)
        assertEquals(2, jp.printingCount)
        assertEquals("normal.png", jp.imageUrl)
        assertEquals(PrintingTitle("Roronoa Zoro", null), english.searchCards("zoro").single().title)
    }

    @Test
    fun `results stay in the session's Language and a blank search finds nothing`() = runTest {
        seed(Language.English, printing("OP01-001", name = "Roronoa Zoro"))
        seed(Language.Japanese, printing("OP01-001", name = "ロロノア・ゾロ", language = Language.Japanese), printing("OP01-099", name = "ゾロの刀", baseNumber = "OP01-099", language = Language.Japanese))
        val (english) = entry(Language.English)

        assertEquals(listOf("OP01-001"), english.searchCards("ゾロ").map { it.baseNumber })
        assertEquals(emptyList(), english.searchCards("刀"))
        assertEquals(emptyList(), english.searchCards("  "))
    }

    @Test
    fun `one search goes straight to proposals for a Base Number, lists Cards for a name and falls back to no match`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro, printing("OP02-016", name = "Nami", baseNumber = "OP02-016"))
        val (entry) = entry()

        assertEquals(ScanSearch.Proposals(ScanLookup.Candidates(listOf(zoro), preselected = zoro)), entry.find(" op01-001 "))
        assertEquals(listOf("OP01-001"), assertIs<ScanSearch.Cards>(entry.find("zoro")).matches.map { it.baseNumber })
        assertEquals(listOf("OP01-001", "OP02-016"), assertIs<ScanSearch.Cards>(entry.find("op0")).matches.map { it.baseNumber })
        assertEquals(ScanSearch.NoMatch("op99-999"), entry.find(" op99-999 "))
    }

    @Test
    fun `a partial Base Number lists its Cards`() = runTest {
        seed(Language.English, printing("OP01-001"), printing("OP01-016", name = "Nami", baseNumber = "OP01-016"), printing("OP02-001", baseNumber = "OP02-001"))
        val (entry) = entry()

        assertEquals(listOf("OP01-001", "OP01-016"), entry.searchCards("op01-0").map { it.baseNumber })
    }

    @Test
    fun `proposals run Variant, then Product code, then Release Product name`() = runTest {
        fun variant(sourceId: String, code: String?, product: String) =
            printing(sourceId).copy(productCode = code, releaseProduct = product)
        val normal = variant("OP01-001", "OP-01", "Romance Dawn")
        val reprintLate = variant("OP01-001_r2", "OP-09", "Emperors in the New World")
        val reprintEarly = variant("OP01-001_r1", "OP-05", "Awakening of the New Era")
        val altNoCode = variant("OP01-001_p3", null, "Alpha promo")
        val altB = variant("OP01-001_p2", "OP-01", "Zeta")
        val altA = variant("OP01-001_p1", "OP-01", "Beta")
        seed(Language.English, altNoCode, altB, reprintLate, normal, altA, reprintEarly)
        val (entry) = entry()

        val lookup = assertIs<ScanLookup.Candidates>(entry.lookup("OP01-001"))

        assertEquals(listOf(normal, reprintEarly, reprintLate, altA, altB, altNoCode), lookup.printings)
    }
}
