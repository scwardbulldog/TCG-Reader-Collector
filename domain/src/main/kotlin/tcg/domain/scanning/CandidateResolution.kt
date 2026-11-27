package tcg.domain.scanning

import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.collection.proposalOrder

/** Candidate Printings for a Base Number in one Language, in proposal order: Normal first, then Reprints, then Alternate Arts. */
suspend fun resolveCandidates(catalog: CatalogStore, game: Game, language: Language, baseNumber: String): List<Printing> =
    catalog.findByBaseNumber(game, language, baseNumber).inCandidateOrder()

internal fun List<Printing>.inCandidateOrder(): List<Printing> = sortedWith(proposalOrder)
