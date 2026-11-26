package tcg.app.storage

import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

class RoomCatalogStore(private val dao: CatalogDao) : CatalogStore {
    override suspend fun replaceCatalog(game: Game, language: Language, printings: List<Printing>, version: String?) {
        dao.replaceAll(game, language, printings.map { it.toEntity() }, version)
    }

    override suspend fun catalogVersion(game: Game, language: Language): String? = dao.version(game, language)

    override suspend fun find(identifier: PrintingIdentifier): Printing? =
        dao.find(identifier.game, identifier.language, identifier.sourceId)?.toDomain()

    override suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing> =
        dao.findByBaseNumber(game, language, baseNumber).map { it.toDomain() }

    override suspend fun search(game: Game, language: Language, query: String): List<Printing> =
        dao.search(game, language, query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")).map { it.toDomain() }
}

private fun Printing.toEntity() = CatalogPrintingEntity(
    game = identifier.game,
    language = identifier.language,
    sourceId = identifier.sourceId,
    baseNumber = baseNumber,
    releaseProduct = releaseProduct,
    rarity = rarity,
    name = name,
    imageUrl = imageUrl,
)

private fun CatalogPrintingEntity.toDomain() = Printing(
    identifier = PrintingIdentifier(game, language, sourceId),
    baseNumber = baseNumber,
    releaseProduct = releaseProduct,
    rarity = rarity,
    name = name,
    imageUrl = imageUrl,
)
