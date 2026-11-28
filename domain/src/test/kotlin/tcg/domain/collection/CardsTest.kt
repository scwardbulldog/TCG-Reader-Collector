package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.fixtureVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardsTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val cards = Cards(catalog, collection)
    private val catalogued = mutableMapOf<Language, List<Printing>>()
    private var customIds = 0

    private fun id(language: Language, sourceId: String) = PrintingIdentifier(Game.OnePiece, language, sourceId)

    private suspend fun list(language: Language, sourceId: String, baseNumber: String, name: String) {
        catalogued[language] = catalogued[language].orEmpty() +
            Printing(id(language, sourceId), baseNumber, "Romance Dawn", "L", name, "https://example.invalid/$sourceId.png", fixtureVariant(sourceId))
        catalog.replaceCatalog(Game.OnePiece, language, catalogued.getValue(language))
    }

    private suspend fun custom(language: Language, baseNumber: String, label: String) =
        CustomPrintings(collection, InMemoryCardPhotoStore(), newId = { "c${customIds++}" }).create(Game.OnePiece, language, baseNumber, label)

    @Test
    fun `a Card lists its Printings by Language then Variant, Custom Printings included`() = runTest {
        list(Language.Japanese, "OP01-001_p1", "OP01-001", "ロロノア・ゾロ")
        list(Language.English, "OP01-001_p1", "OP01-001", "Roronoa Zoro")
        list(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
        list(Language.English, "OP01-001_r1", "OP01-001", "Roronoa Zoro")
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        list(Language.English, "OP01-002", "OP01-002", "Trafalgar Law")
        val mine = custom(Language.English, "OP01-001", "My signed Zoro")

        val card = cards.of(Game.OnePiece, "OP01-001")!!

        assertEquals(
            listOf("OP01-001", "OP01-001_r1", "OP01-001_p1", mine.identifier.sourceId, "OP01-001", "OP01-001_p1"),
            card.printings.map { it.identifier.sourceId },
        )
        assertEquals(
            listOf(Language.English, Language.English, Language.English, Language.English, Language.Japanese, Language.Japanese),
            card.printings.map { it.identifier.language },
        )
    }

    @Test
    fun `a base number with no Printing is no Card`() = runTest {
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")

        assertNull(cards.of(Game.OnePiece, "OP99-999"))
    }

    @Test
    fun `the Card name in a Language is the Normal Printing's name, else any Printing's name there`() = runTest {
        list(Language.English, "OP01-001_p1", "OP01-001", "Zoro (Alternate Art)")
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        list(Language.Japanese, "OP01-001_p1", "OP01-001", "ゾロ (パラレル)")

        val card = cards.of(Game.OnePiece, "OP01-001")!!

        assertEquals("Roronoa Zoro", card.name(Language.English))
        assertEquals("ゾロ (パラレル)", card.name(Language.Japanese))
    }

    @Test
    fun `the English and the Japanese name find the same Card and its Printings`() = runTest {
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        list(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
        list(Language.Japanese, "OP01-001_p1", "OP01-001", "ロロノア・ゾロ")
        list(Language.English, "OP01-002", "OP01-002", "Trafalgar Law")

        val byEnglish = cards.search(Game.OnePiece, "zoro")
        val byJapanese = cards.search(Game.OnePiece, "ゾロ")

        assertEquals(listOf("OP01-001"), byEnglish.map { it.baseNumber })
        assertEquals(byEnglish, byJapanese)
        assertEquals(3, byEnglish.single().printings.size)
    }

    @Test
    fun `a Language scope drops Cards with no Printing in it`() = runTest {
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        list(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
        list(Language.Japanese, "OP01-099", "OP01-099", "ゾロの刀")

        assertEquals(listOf("OP01-001", "OP01-099"), cards.search(Game.OnePiece, "ゾロ").map { it.baseNumber })
        assertEquals(listOf("OP01-001"), cards.search(Game.OnePiece, "ゾロ", Language.English).map { it.baseNumber })
    }

    @Test
    fun `search folds full-width and half-width forms`() = runTest {
        list(Language.Japanese, "OP01-010", "OP01-010", "Ｄ・ルフィ")
        list(Language.Japanese, "OP01-011", "OP01-011", "Q＆A")
        list(Language.English, "OP01-012", "OP01-012", "Chopper")

        assertEquals(listOf("OP01-010"), cards.search(Game.OnePiece, "D・").map { it.baseNumber })
        assertEquals(listOf("OP01-011"), cards.search(Game.OnePiece, "q&a").map { it.baseNumber })
        assertEquals(listOf("OP01-012"), cards.search(Game.OnePiece, "ＣＨＯＰ").map { it.baseNumber })
    }

    @Test
    fun `a Custom label does not make a Card match`() = runTest {
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        custom(Language.English, "OP01-001", "Signed by Oda")

        assertEquals(emptyList(), cards.search(Game.OnePiece, "oda"))
    }

    @Test
    fun `a Custom Printing is listed on the Card a name search finds`() = runTest {
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val mine = custom(Language.English, "OP01-001", "Signed by Oda")

        val found = cards.search(Game.OnePiece, "zoro").single()

        assertTrue(mine in found.printings)
    }

    @Test
    fun `a Custom Printing's label is never a Card name`() = runTest {
        list(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        custom(Language.Japanese, "OP01-001", "My Japanese Zoro")

        val card = cards.of(Game.OnePiece, "OP01-001")!!

        assertNull(card.name(Language.Japanese))
        assertEquals("Roronoa Zoro", card.name(Language.English))
    }
}
