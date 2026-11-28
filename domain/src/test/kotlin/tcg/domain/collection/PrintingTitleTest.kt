package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.InMemoryCardPhotoStore
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.fixtureVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintingTitleTest {
    private val collection = InMemoryCollectionStore()
    private val catalog = InMemoryCatalogStore(collection)
    private val titles = PrintingTitles(catalog)

    private fun id(language: Language, sourceId: String) = PrintingIdentifier(Game.OnePiece, language, sourceId)

    private fun printing(language: Language, sourceId: String, baseNumber: String, name: String) =
        Printing(id(language, sourceId), baseNumber, "ROMANCE DAWN", "L", name, "", fixtureVariant(sourceId))

    private suspend fun list(language: Language, vararg printings: Printing) =
        catalog.replaceCatalog(Game.OnePiece, language, printings.toList())

    private val enZoro = printing(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
    private val jpZoro = printing(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
    private val jpZoroAlt = printing(Language.Japanese, "OP01-001_p1", "OP01-001", "ロロノア・ゾロ (パラレル)")

    @Test
    fun `a Japanese Printing is titled with the Card's English name and keeps its own name as subtext`() = runTest {
        list(Language.English, enZoro)
        list(Language.Japanese, jpZoro, jpZoroAlt)

        assertEquals(PrintingTitle("Roronoa Zoro", "ロロノア・ゾロ"), titles.of(jpZoro))
        assertEquals(PrintingTitle("Roronoa Zoro", "ロロノア・ゾロ (パラレル)"), titles.of(jpZoroAlt))
    }

    @Test
    fun `an English Printing shows the English name alone`() = runTest {
        list(Language.English, enZoro)
        list(Language.Japanese, jpZoro)

        assertEquals(PrintingTitle("Roronoa Zoro", null), titles.of(enZoro))
    }

    @Test
    fun `a Card with only Japanese Printings shows the Japanese name alone`() = runTest {
        val promo = printing(Language.Japanese, "P-001", "P-001", "プロモ")
        list(Language.Japanese, promo)

        assertEquals(PrintingTitle("プロモ", null), titles.of(promo))
        assertNull(titles.englishName(Game.OnePiece, "P-001"))
    }

    @Test
    fun `a Custom Printing is titled with its label and the English Card name as subtext when the Catalog has one`() = runTest {
        list(Language.English, enZoro)
        val custom = CustomPrintings(collection, InMemoryCardPhotoStore(), newId = { "c0" })
        val stamped = custom.create(Game.OnePiece, Language.Japanese, "OP01-001", "Milk Box Stamp")
        val lone = custom.create(Game.OnePiece, Language.Japanese, "OP99-001", "Mystery card")

        assertEquals(PrintingTitle("Milk Box Stamp", "Roronoa Zoro"), titles.of(stamped))
        assertEquals(PrintingTitle("Mystery card", null), titles.of(lone))
    }

    @Test
    fun `a Custom label never becomes the English name of other Printings`() = runTest {
        list(Language.Japanese, jpZoro)
        CustomPrintings(collection, InMemoryCardPhotoStore(), newId = { "c0" }).create(Game.OnePiece, Language.English, "OP01-001", "My Zoro")

        assertEquals(PrintingTitle("ロロノア・ゾロ", null), titles.of(jpZoro))
    }

    @Test
    fun `the English name is the Normal Printing's, not an Alternate Art's`() = runTest {
        list(Language.English, printing(Language.English, "OP01-001_p1", "OP01-001", "Zoro (Alt)"), enZoro)

        assertEquals("Roronoa Zoro", titles.englishName(Game.OnePiece, "OP01-001"))
    }

    @Test
    fun `Treatment shows only when known, never as plain`() {
        val unknown = enZoro
        val none = enZoro.copy(treatments = emptyList())
        val tagged = enZoro.copy(treatments = listOf("Parallel", "Manga"))

        assertNull(unknown.treatmentText)
        assertNull(none.treatmentText)
        assertEquals("Parallel, Manga", tagged.treatmentText)
    }

    @Test
    fun `the Release Product shows with its Product code when it has one`() {
        assertEquals("ROMANCE DAWN", enZoro.productText)
        assertEquals("ROMANCE DAWN (OP-01)", enZoro.copy(productCode = "OP-01").productText)
        assertEquals("BOOSTER PACK [OP-01]", enZoro.copy(releaseProduct = "BOOSTER PACK [OP-01]", productCode = "OP-01").productText)
        assertEquals("OP-01", enZoro.copy(releaseProduct = "", productCode = "OP-01").productText)
        assertNull(enZoro.copy(releaseProduct = "").productText)
    }

    @Test
    fun `the CSV carries the Card's English name for a Japanese Printing and leaves Name as its own`() = runTest {
        list(Language.English, enZoro)
        list(Language.Japanese, jpZoro)
        collection.add(NewOwnedCopy(jpZoro.identifier, Condition.NearMint))

        val row = CollectionExport(collection, catalog).csv().lines()[1]

        assertEquals("onepiece,JP,OP01-001,onepiece / JP / OP01-001,ロロノア・ゾロ,Roronoa Zoro,Normal,ROMANCE DAWN,Near Mint,,", row)
    }
}
