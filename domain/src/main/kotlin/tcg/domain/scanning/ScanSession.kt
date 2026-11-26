package tcg.domain.scanning

import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location

/**
 * What the collector has picked so far. Language and Location have no default: Language is never
 * inferred and the session cannot start until both are chosen.
 */
data class ScanSetup(
    val language: Language? = null,
    val location: Location? = null,
    val condition: Condition = Condition.NearMint,
) {
    val canStart: Boolean get() = language != null && location != null

    fun start(): ScanSession? {
        val language = language ?: return null
        val location = location ?: return null
        return ScanSession(Game.OnePiece, language, location, condition)
    }
}

/**
 * Settings of one Scan Session. A different Game or Language is a different session, so neither can
 * be changed here: start a new session instead.
 */
class ScanSession(
    val game: Game,
    val language: Language,
    val location: Location,
    val defaultCondition: Condition,
) {
    val scope: ScanScope get() = ScanScope(game, language)
}
