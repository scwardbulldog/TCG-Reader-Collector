package tcg.domain.scanning

import kotlinx.coroutines.test.runTest
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.collection.InMemoryCollectionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScanSessionSetupTest {
    private val collection = InMemoryCollectionStore()

    @Test
    fun `a session cannot start until the collector has chosen a Language`() = runTest {
        val binder = collection.createLocation("Binder 1")

        assertNull(ScanSetup(location = binder).start())
    }

    @Test
    fun `a session cannot start without a Location`() {
        assertNull(ScanSetup(language = Language.English).start())
    }

    @Test
    fun `EN and JP are both selectable and the session is for One Piece in the chosen Language and Location`() = runTest {
        val binder = collection.createLocation("Binder 1")

        for (language in Language.entries) {
            val session = assertNotNull(ScanSetup(language = language, location = binder, condition = Condition.LightlyPlayed).start())

            assertEquals(Game.OnePiece, session.game)
            assertEquals(language, session.language)
            assertEquals(binder, session.location)
            assertEquals(Condition.LightlyPlayed, session.defaultCondition)
            assertEquals(ScanScope(Game.OnePiece, language), session.scope)
        }
    }

    @Test
    fun `the default condition is Near Mint unless the collector picks another`() = runTest {
        val binder = collection.createLocation("Binder 1")

        val session = assertNotNull(ScanSetup(language = Language.Japanese, location = binder).start())

        assertEquals(Condition.NearMint, session.defaultCondition)
    }
}
