package tcg.domain.scanning

import kotlinx.coroutines.test.runTest
import tcg.domain.CatalogStore
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.collection.InMemoryCollectionStore
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
    ) = Printing(PrintingIdentifier(Game.OnePiece, language, sourceId), baseNumber, "Product $sourceId", "L", name, "")

    private suspend fun seed(language: Language, vararg printings: Printing) =
        catalog.replaceCatalog(Game.OnePiece, language, printings.toList())

    private suspend fun entry(
        language: Language = Language.English,
        condition: Condition = Condition.NearMint,
        catalog: CatalogStore = this.catalog,
    ): Pair<ScanSessionInput, Location> {
        val binder = collection.createLocation("Binder 1")
        val session = ScanSession(Game.OnePiece, language, binder, condition)
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
    fun `confirming adds one Owned Copy with the session Location and default condition`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry, binder) = entry(condition = Condition.LightlyPlayed)

        val added = entry.add(zoro)

        assertEquals(1, added.size)
        assertEquals(zoro.identifier, added.single().printing)
        assertEquals(binder.id, added.single().location)
        assertEquals(Condition.LightlyPlayed, added.single().condition)
        assertEquals(added, collection.ownedCopies(zoro.identifier))
    }

    @Test
    fun `a bulk add of N creates N separate Owned Copies sharing Printing, Location and condition`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry, binder) = entry(condition = Condition.NearMint)

        val added = entry.add(zoro, count = 10)

        assertEquals(10, added.size)
        assertEquals(10, added.map { it.id }.toSet().size)
        assertTrue(added.all { it.printing == zoro.identifier && it.location == binder.id && it.condition == Condition.NearMint })
        assertEquals(mapOf(zoro.identifier to 10), collection.countsByPrinting())
    }

    @Test
    fun `a condition override applies to one add only and leaves the session default alone`() = runTest {
        val zoro = printing("OP01-001")
        seed(Language.English, zoro)
        val (entry) = entry(condition = Condition.NearMint)

        val overridden = entry.add(zoro, condition = Condition.Damaged)
        val next = entry.add(zoro)

        assertEquals(Condition.Damaged, overridden.single().condition)
        assertEquals(Condition.NearMint, next.single().condition)
        assertEquals(Condition.NearMint, entry.session.defaultCondition)
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
    fun `manual search matches Base Number or name within the session Language only`() = runTest {
        val zoro = printing("OP01-001")
        val zoroAlt = printing("OP01-001_p1")
        val nami = printing("OP01-016", name = "Nami", baseNumber = "OP01-016")
        val japaneseZoro = printing("OP01-001", name = "ロロノア・ゾロ", language = Language.Japanese)
        seed(Language.English, nami, zoroAlt, zoro)
        seed(Language.Japanese, japaneseZoro)
        val (english) = entry(Language.English)
        val (japanese) = entry(Language.Japanese)

        assertEquals(listOf(zoro, zoroAlt), english.search("zoro"))
        assertEquals(listOf(zoro, zoroAlt), english.search("op01-001"))
        assertEquals(listOf(nami), english.search("OP01-016"))
        assertEquals(listOf(japaneseZoro), japanese.search("OP01-001"))
        assertEquals(emptyList(), japanese.search("nami"))
        assertEquals(emptyMap(), collection.countsByPrinting())
    }

    @Test
    fun `a blank search finds nothing`() = runTest {
        seed(Language.English, printing("OP01-001"))
        val (entry) = entry()

        assertEquals(emptyList(), entry.search("  "))
    }
}
