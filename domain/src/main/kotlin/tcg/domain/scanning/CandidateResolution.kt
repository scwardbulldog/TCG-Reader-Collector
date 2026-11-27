package tcg.domain.scanning

import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.Variant
import tcg.domain.onepiece.OnePiece

/** Candidate Printings for a Base Number in one Language: Normal first, Reprints beneath it, then Alternate Arts. */
suspend fun resolveCandidates(catalog: CatalogStore, game: Game, language: Language, baseNumber: String): List<Printing> =
    catalog.findByBaseNumber(game, language, baseNumber).inCandidateOrder()

internal fun List<Printing>.inCandidateOrder(): List<Printing> =
    sortedWith(compareBy<Printing>({ candidateRank(it) }, { it.identifier.sourceId }))

internal fun candidateRank(printing: Printing): Int = when (OnePiece.variantOf(printing.identifier)) {
    Variant.Normal -> 0
    Variant.Reprint -> 1
    Variant.AlternateArt -> 2
    null -> 3
}
