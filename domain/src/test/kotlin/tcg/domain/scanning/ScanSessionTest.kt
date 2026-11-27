package tcg.domain.scanning

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.collection.InMemoryCollectionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class ScanSessionTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val languages = InMemoryLanguageStore()

    private fun printing(language: Language, name: String = "Roronoa Zoro") =
        Printing(PrintingIdentifier(Game.OnePiece, language, "OP01-001"), "OP01-001", "Product", "L", name, "")

    private fun input() = ScanSessionInput(ScanSession(Game.OnePiece, languages), catalog, collection)

    @Test
    fun `a session is English on first launch`() {
        assertEquals(Language.English, ScanSession(Game.OnePiece, languages).language)
    }

    @Test
    fun `switching Language changes the scope and is remembered for the next session`() {
        val session = ScanSession(Game.OnePiece, languages)

        session.switchLanguage(Language.Japanese)

        assertEquals(Language.Japanese, session.language)
        assertEquals(ScanScope(Game.OnePiece, Language.Japanese), session.scope)
        assertEquals(Language.Japanese, ScanSession(Game.OnePiece, languages).language)
    }

    @Test
    fun `lookups and manual search follow the current Language only`() = runTest {
        val english = printing(Language.English)
        val japanese = printing(Language.Japanese, name = "ロロノア・ゾロ")
        catalog.replaceCatalog(Game.OnePiece, Language.English, listOf(english))
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(japanese))
        val input = input()

        assertEquals(ScanLookup.Candidates(listOf(english), english), input.lookup("OP01-001"))
        assertEquals(listOf(english), input.search("OP01-001"))

        input.session.switchLanguage(Language.Japanese)

        assertEquals(ScanLookup.Candidates(listOf(japanese), japanese), input.lookup("OP01-001"))
        assertEquals(listOf(japanese), input.search("OP01-001"))
    }

    @Test
    fun `after a switch only a Printing of the new Language can be added`() = runTest {
        val input = input()
        input.session.switchLanguage(Language.Japanese)

        assertFailsWith<IllegalArgumentException> { input.add(printing(Language.English)) }
        assertEquals(emptyMap(), collection.countsByPrinting())

        val added = input.add(printing(Language.Japanese))
        assertEquals(printing(Language.Japanese).identifier, added.single().printing)
    }

    @Test
    fun `a Custom Printing is created in the current Language`() = runTest {
        val input = input()
        input.session.switchLanguage(Language.Japanese)

        val created = input.createCustomPrinting("OP01-001", "Milk Box Stamp")

        assertEquals(Language.Japanese, created.identifier.language)
        assertIs<ScanLookup.Candidates>(input.lookup("OP01-001"))
    }

    @Test
    fun `a session has no Location until an add supplies one, then offers the last one`() = runTest {
        val input = input()
        val binder = collection.createLocation("Binder 1")
        val box = collection.createLocation("Bulk box")
        assertNull(input.session.location)

        input.add(printing(Language.English), location = binder)
        assertEquals(binder, input.session.location)

        input.add(printing(Language.English), location = box)
        assertEquals(box, input.session.location)
    }

    @Test
    fun `an add with no Location is saved without one`() = runTest {
        val input = input()

        val copy = input.add(printing(Language.English), location = null).single()

        assertNull(copy.location)
        assertEquals(listOf(copy), collection.ownedCopies(copy.printing))
    }

    @Test
    fun `every add defaults to Near Mint and a condition override applies to that add only`() = runTest {
        val input = input()

        val overridden = input.add(printing(Language.English), condition = Condition.Damaged).single()
        val next = input.add(printing(Language.English)).single()

        assertEquals(Condition.Damaged, overridden.condition)
        assertEquals(Condition.NearMint, next.condition)
    }

    @Test
    fun `Add all in a Bulk Add uses the one Location it is given and the current Language`() = runTest {
        val japanese = printing(Language.Japanese)
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(japanese))
        val input = input()
        input.session.switchLanguage(Language.Japanese)
        val batch = Batch(input)
        val binder = collection.createLocation("Binder 1")
        batch.read(PhotoId(1), "OP01-001")
        batch.read(PhotoId(2), "OP01-001")

        val added = batch.addAll(binder)

        assertEquals(2, added.size)
        assertEquals(setOf(binder.id), added.map { it.location }.toSet())
        assertEquals(setOf(japanese.identifier), added.map { it.printing }.toSet())
        assertEquals(binder, input.session.location)
    }
}
