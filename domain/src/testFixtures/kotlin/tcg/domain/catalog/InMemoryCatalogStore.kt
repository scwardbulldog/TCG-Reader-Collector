package tcg.domain.catalog

import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

class InMemoryCatalogStore : CatalogStore {
    private val printings = mutableMapOf<CatalogTarget, List<Printing>>()
    private val versions = mutableMapOf<CatalogTarget, String?>()

    override suspend fun replaceCatalog(game: Game, language: Language, printings: List<Printing>, version: String?) {
        this.printings[CatalogTarget(game, language)] = printings
        versions[CatalogTarget(game, language)] = version
    }

    override suspend fun catalogVersion(game: Game, language: Language): String? =
        versions[CatalogTarget(game, language)]

    override suspend fun find(identifier: PrintingIdentifier): Printing? =
        printings[CatalogTarget(identifier.game, identifier.language)].orEmpty().firstOrNull { it.identifier == identifier }

    override suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing> =
        printings[CatalogTarget(game, language)].orEmpty().filter { it.baseNumber == baseNumber }

    override suspend fun search(game: Game, language: Language, query: String): List<Printing> =
        printings[CatalogTarget(game, language)].orEmpty().filter {
            it.baseNumber.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        }
}
