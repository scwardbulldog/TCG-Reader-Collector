package tcg.domain.scanning

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.InMemoryCatalogStore
import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateResolutionTest {
    private val catalog = InMemoryCatalogStore()

    private fun printing(sourceId: String, language: Language = Language.English, baseNumber: String = "OP01-001") =
        Printing(PrintingIdentifier(Game.OnePiece, language, sourceId), baseNumber, "Product $sourceId", "L", "Zoro", "")

    @Test
    fun `Normal comes first, Reprints beneath it, then Alternate Arts, whatever the Catalog order`() = runTest {
        catalog.replaceCatalog(
            Game.OnePiece,
            Language.English,
            listOf(
                printing("OP01-001_p2"),
                printing("OP01-001_r1"),
                printing("OP01-001_p1"),
                printing("OP01-001"),
                printing("OP01-001_r2"),
            ),
        )

        val candidates = resolveCandidates(catalog, Game.OnePiece, Language.English, "OP01-001")

        assertEquals(
            listOf("OP01-001", "OP01-001_r1", "OP01-001_r2", "OP01-001_p1", "OP01-001_p2"),
            candidates.map { it.identifier.sourceId },
        )
    }

    @Test
    fun `only Printings of the requested Base Number and Language are candidates`() = runTest {
        catalog.replaceCatalog(
            Game.OnePiece,
            Language.English,
            listOf(printing("OP01-001"), printing("OP01-002", baseNumber = "OP01-002")),
        )
        catalog.replaceCatalog(Game.OnePiece, Language.Japanese, listOf(printing("OP01-001", Language.Japanese)))

        val candidates = resolveCandidates(catalog, Game.OnePiece, Language.Japanese, "OP01-001")

        assertEquals(listOf(Language.Japanese), candidates.map { it.identifier.language })
    }

    @Test
    fun `an unknown Base Number has no candidates`() = runTest {
        assertEquals(emptyList(), resolveCandidates(catalog, Game.OnePiece, Language.English, "OP99-999"))
    }
}
