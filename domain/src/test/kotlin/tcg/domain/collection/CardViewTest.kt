package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.fixtureVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardViewTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val view = CardView(catalog, collection)
    private val catalogued = mutableMapOf<Language, List<Printing>>()

    private fun id(language: Language, sourceId: String) = PrintingIdentifier(Game.OnePiece, language, sourceId)

    private suspend fun list(
        language: Language,
        sourceId: String,
        name: String,
        treatments: List<String>? = null,
        baseNumber: String = "OP01-001",
    ): PrintingIdentifier {
        val identifier = id(language, sourceId)
        catalogued[language] = catalogued[language].orEmpty() +
            Printing(identifier, baseNumber, "Romance Dawn", "L", name, "", fixtureVariant(sourceId), productCode = "OP-01", treatments = treatments)
        catalog.replaceCatalog(Game.OnePiece, language, catalogued.getValue(language))
        return identifier
    }

    @Test
    fun `lists every Printing across Languages grouped by Language then Variant with owned counts`() = runTest {
        val jpNormal = list(Language.Japanese, "OP01-001", "ロロノア・ゾロ")
        val enAlt = list(Language.English, "OP01-001_p1", "Roronoa Zoro", treatments = listOf("Parallel"))
        val enNormal = list(Language.English, "OP01-001", "Roronoa Zoro")
        val enReprint = list(Language.English, "OP01-001_r1", "Roronoa Zoro")
        list(Language.English, "OP01-016", "Nami", baseNumber = "OP01-016")
        repeat(2) { collection.add(NewOwnedCopy(enAlt)) }
        collection.add(NewOwnedCopy(jpNormal))

        val card = view.of(Game.OnePiece, "OP01-001")!!

        assertEquals("OP01-001", card.baseNumber)
        assertEquals("Roronoa Zoro", card.name)
        assertEquals(listOf(Language.English, Language.Japanese), card.languages.map { it.language })
        val english = card.languages[0]
        assertEquals(listOf(Variant.Normal, Variant.Reprint, Variant.AlternateArt), english.variants.map { it.variant })
        assertEquals(
            listOf(enNormal to 0, enReprint to 0, enAlt to 2),
            english.variants.flatMap { it.entries }.map { it.printing.identifier to it.owned },
        )
        assertEquals(listOf(jpNormal to 1), card.languages[1].variants.flatMap { it.entries }.map { it.printing.identifier to it.owned })
    }

    @Test
    fun `a Treatment is shown only where published, never as plain`() = runTest {
        list(Language.English, "OP01-001", "Roronoa Zoro")
        list(Language.English, "OP01-001_p1", "Roronoa Zoro", treatments = listOf("Parallel", "Manga"))

        val entries = view.of(Game.OnePiece, "OP01-001")!!.languages.single().variants.flatMap { it.entries }

        assertEquals(listOf(null, "Parallel, Manga"), entries.map { it.printing.treatmentText })
    }

    @Test
    fun `a Card with only a Japanese Printing is named in Japanese, and a base number with no Printing has no Card view`() = runTest {
        list(Language.Japanese, "OP01-001", "ロロノア・ゾロ")

        assertEquals("ロロノア・ゾロ", view.of(Game.OnePiece, "OP01-001")!!.name)
        assertNull(view.of(Game.OnePiece, "OP99-999"))
    }

    @Test
    fun `a Custom Printing is listed after the Catalog Printings of its Language`() = runTest {
        list(Language.English, "OP01-001", "Roronoa Zoro")
        val mine = CustomPrintings(collection, InMemoryCardPhotoStore()).create(Game.OnePiece, Language.English, "OP01-001", "Milk Box Stamp")
        collection.add(NewOwnedCopy(mine.identifier))

        val variants = view.of(Game.OnePiece, "OP01-001")!!.languages.single().variants

        assertEquals(listOf<Variant?>(Variant.Normal, null), variants.map { it.variant })
        assertEquals(1, variants.last().entries.single().owned)
    }
}
