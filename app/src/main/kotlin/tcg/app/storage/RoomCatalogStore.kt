package tcg.app.storage

import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.containsFolded

class RoomCatalogStore(private val dao: CatalogDao) : CatalogStore {
    override suspend fun replaceCatalog(game: Game, language: Language, printings: List<Printing>, version: String?) {
        dao.replaceAll(game, language, printings, version)
    }

    override suspend fun catalogVersion(game: Game, language: Language): String? = dao.version(game, language)

    override suspend fun find(identifier: PrintingIdentifier): Printing? =
        dao.find(identifier.game, identifier.language, identifier.sourceId)?.toDomain()

    override suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing> =
        dao.findByBaseNumber(game, language, baseNumber).map { it.toDomain() }

    /** SQL `LIKE` cannot fold full-width forms, so one Language's rows (a few thousand) are filtered here. */
    override suspend fun search(game: Game, language: Language, query: String): List<Printing> =
        dao.searchable(game, language)
            .filter { it.baseNumber.containsFolded(query) || it.name.containsFolded(query) }
            .map { it.toDomain() }
}
