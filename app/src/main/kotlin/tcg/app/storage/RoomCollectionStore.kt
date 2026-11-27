package tcg.app.storage

import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import tcg.domain.DeleteLocationResult
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

class RoomCollectionStore(private val dao: CollectionDao) : CollectionStore {
    override suspend fun add(copy: NewOwnedCopy): OwnedCopy {
        val id = dao.insert(copy.toEntity())
        return OwnedCopy(OwnedCopyId(id), copy.printing, copy.condition, copy.location, copy.notes)
    }

    override suspend fun addAll(copies: List<NewOwnedCopy>): List<OwnedCopy> {
        val ids = dao.insertAll(copies.map { it.toEntity() })
        return copies.zip(ids) { copy, id ->
            OwnedCopy(OwnedCopyId(id), copy.printing, copy.condition, copy.location, copy.notes)
        }
    }

    private fun NewOwnedCopy.toEntity() = OwnedCopyEntity(
        game = printing.game,
        language = printing.language,
        sourceId = printing.sourceId,
        condition = condition,
        locationId = location?.value,
        notes = notes,
    )

    override suspend fun ownedCopies(printing: PrintingIdentifier): List<OwnedCopy> =
        dao.ownedCopies(printing.game, printing.language, printing.sourceId).map { it.toDomain() }

    override suspend fun update(copy: OwnedCopy) = dao.update(
        OwnedCopyEntity(
            id = copy.id.value,
            game = copy.printing.game,
            language = copy.printing.language,
            sourceId = copy.printing.sourceId,
            condition = copy.condition,
            locationId = copy.location?.value,
            notes = copy.notes,
        ),
    )

    override suspend fun delete(id: OwnedCopyId) = dao.delete(id.value)

    override suspend fun countsByPrinting(): Map<PrintingIdentifier, Int> =
        dao.countsByPrinting().associate { PrintingIdentifier(it.game, it.language, it.sourceId) to it.ownedCopyCount }

    override suspend fun rememberPrintings(printings: List<Printing>) =
        dao.upsertKnownPrintings(printings.map { it.toKnownEntity() })

    override suspend fun lastKnownPrinting(identifier: PrintingIdentifier): Printing? =
        dao.knownPrinting(identifier.game, identifier.language, identifier.sourceId)?.toDomain()

    override suspend fun customPrintings(game: Game, language: Language): List<Printing> =
        dao.customPrintings(game, language).map { it.toDomain() }

    override suspend fun deleteCustomPrinting(identifier: PrintingIdentifier) =
        dao.deleteCustomPrinting(identifier.game, identifier.language, identifier.sourceId)

    override suspend fun createLocation(name: String): Location =
        Location(LocationId(dao.insertLocation(LocationEntity(name = name))), name)

    override suspend fun locations(): List<Location> =
        dao.locations().map { Location(LocationId(it.id), it.name) }

    override suspend fun renameLocation(id: LocationId, name: String) =
        dao.renameLocation(id.value, name)

    override suspend fun deleteLocation(id: LocationId): DeleteLocationResult =
        if (dao.deleteLocationIfEmpty(id.value)) DeleteLocationResult.Deleted else DeleteLocationResult.NotEmpty

    override suspend fun snapshot(): CollectionSnapshot {
        val (locations, copies, custom) = dao.snapshot()
        return CollectionSnapshot(
            locations.map { Location(LocationId(it.id), it.name) },
            copies.map { it.toDomain() },
            custom.map { it.toDomain() },
        )
    }

    override suspend fun replaceCollection(snapshot: CollectionSnapshot) = dao.replaceAll(
        snapshot.locations.map { LocationEntity(it.id.value, it.name) },
        snapshot.copies.map {
            OwnedCopyEntity(
                id = it.id.value,
                game = it.printing.game,
                language = it.printing.language,
                sourceId = it.printing.sourceId,
                condition = it.condition,
                locationId = it.location?.value,
                notes = it.notes,
            )
        },
        snapshot.customPrintings.map { it.toKnownEntity() },
    )
}

private fun Printing.toKnownEntity() = KnownPrintingEntity(
    game = identifier.game,
    language = identifier.language,
    sourceId = identifier.sourceId,
    baseNumber = baseNumber,
    releaseProduct = releaseProduct,
    rarity = rarity,
    name = name,
    imageUrl = imageUrl,
)

private fun KnownPrintingEntity.toDomain() = Printing(
    identifier = PrintingIdentifier(game, language, sourceId),
    baseNumber = baseNumber,
    releaseProduct = releaseProduct,
    rarity = rarity,
    name = name,
    imageUrl = imageUrl,
)

private fun OwnedCopyEntity.toDomain() = OwnedCopy(
    id = OwnedCopyId(id),
    printing = PrintingIdentifier(game, language, sourceId),
    condition = condition,
    location = locationId?.let(::LocationId),
    notes = notes,
)
