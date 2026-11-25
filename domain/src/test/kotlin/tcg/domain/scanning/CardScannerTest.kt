package tcg.domain.scanning

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class CardScannerTest {
    @Test
    fun `scanner reports Base Number reads for the Scan Session's Game and Language`() = runTest {
        val fake = FakeCardScanner(
            reads = listOf(BaseNumberRead("OP01-001"), BaseNumberRead("OP01-001"), BaseNumberRead("OP02-013")),
        )
        val scanner: CardScanner = fake
        val scope = ScanScope(Game.OnePiece, Language.Japanese)

        val reads = scanner.scan(scope).toList()

        assertEquals(listOf("OP01-001", "OP01-001", "OP02-013"), reads.map { it.baseNumber })
        assertEquals(listOf(scope), fake.requestedScopes)
    }
}
