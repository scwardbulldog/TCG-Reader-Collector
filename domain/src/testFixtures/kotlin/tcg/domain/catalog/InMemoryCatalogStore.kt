package tcg.domain.catalog

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.containsFolded

/** [collection], when given, is where Owned Copies are read from to decide which dropped Printings a refresh keeps. */
class InMemoryCatalogStore(private val collection: CollectionStore? = null) : CatalogStore {
    private val printings = mutableMapOf<CatalogTarget, List<Printing>>()
    private val versions = mutableMapOf<CatalogTarget, String?>()

    override suspend fun replaceCatalog(game: Game, language: Language, printings: List<Printing>, version: String?) {
        val target = CatalogTarget(game, language)
        val inUse = collection?.countsByPrinting()?.keys.orEmpty()
        this.printings[target] = CatalogRefresh.apply(this.printings[target].orEmpty(), printings, inUse)
        versions[target] = version
    }

    override suspend fun catalogVersion(game: Game, language: Language): String? =
        versions[CatalogTarget(game, language)]

    override suspend fun find(identifier: PrintingIdentifier): Printing? =
        printings[CatalogTarget(identifier.game, identifier.language)].orEmpty().firstOrNull { it.identifier == identifier }

    override suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing> =
        current(game, language).filter { it.baseNumber == baseNumber }

    override suspend fun search(game: Game, language: Language, query: String): List<Printing> =
        current(game, language).filter { it.baseNumber.containsFolded(query) || it.name.containsFolded(query) }

    private fun current(game: Game, language: Language) =
        printings[CatalogTarget(game, language)].orEmpty().filter { !it.noLongerInCatalog }
}
