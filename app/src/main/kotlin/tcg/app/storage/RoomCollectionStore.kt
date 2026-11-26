package tcg.app.storage

import tcg.domain.CollectionStore
import tcg.domain.DeleteLocationResult
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.PrintingIdentifier

class RoomCollectionStore(private val dao: CollectionDao) : CollectionStore {
    override suspend fun add(copy: NewOwnedCopy): OwnedCopy {
        val id = dao.insert(
            OwnedCopyEntity(
                game = copy.printing.game,
                language = copy.printing.language,
                sourceId = copy.printing.sourceId,
                condition = copy.condition,
                locationId = copy.location?.value,
                notes = copy.notes,
            ),
        )
        return OwnedCopy(OwnedCopyId(id), copy.printing, copy.condition, copy.location, copy.notes)
    }

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

    override suspend fun createLocation(name: String): Location =
        Location(LocationId(dao.insertLocation(LocationEntity(name = name))), name)

    override suspend fun locations(): List<Location> =
        dao.locations().map { Location(LocationId(it.id), it.name) }

    override suspend fun renameLocation(id: LocationId, name: String) =
        dao.renameLocation(id.value, name)

    override suspend fun deleteLocation(id: LocationId): DeleteLocationResult =
        if (dao.deleteLocationIfEmpty(id.value)) DeleteLocationResult.Deleted else DeleteLocationResult.NotEmpty
}

private fun OwnedCopyEntity.toDomain() = OwnedCopy(
    id = OwnedCopyId(id),
    printing = PrintingIdentifier(game, language, sourceId),
    condition = condition,
    location = locationId?.let(::LocationId),
    notes = notes,
)
