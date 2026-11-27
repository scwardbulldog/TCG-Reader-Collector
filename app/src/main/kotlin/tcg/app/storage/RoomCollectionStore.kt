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
import tcg.domain.isCustom

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

    override suspend fun update(copy: OwnedCopy) =
        dao.update(copy.id.value, copy.condition, copy.location?.value, copy.notes)

    override suspend fun move(id: OwnedCopyId, printing: PrintingIdentifier) =
        dao.move(id.value, printing.game, printing.language, printing.sourceId)

    override suspend fun delete(id: OwnedCopyId) = dao.delete(id.value)

    override suspend fun countsByPrinting(): Map<PrintingIdentifier, Int> =
        dao.countsByPrinting().associate { PrintingIdentifier(it.game, it.language, it.sourceId) to it.ownedCopyCount }

    override suspend fun saveCustomPrinting(printing: Printing) {
        require(printing.identifier.isCustom) { "Only a Custom Printing can be saved here" }
        dao.upsertCustomPrintings(listOf(printing.toEntity()))
    }

    override suspend fun customPrinting(identifier: PrintingIdentifier): Printing? =
        dao.customPrinting(identifier.game, identifier.language, identifier.sourceId)?.toDomain()

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
        val rows = dao.snapshot()
        return CollectionSnapshot(
            rows.locations.map { Location(LocationId(it.id), it.name) },
            rows.copies.map { it.toDomain() },
            rows.customPrintings.map { it.toDomain() },
            rows.lastKnownPrintings.map { it.toDomain() },
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
        snapshot.customPrintings.map { it.toEntity() },
        snapshot.lastKnownPrintings
            .filter { printing -> snapshot.copies.any { it.printing == printing.identifier } }
            .map { it.copy(noLongerInCatalog = true).toEntity() },
    )
}

private fun OwnedCopyEntity.toDomain() = OwnedCopy(
    id = OwnedCopyId(id),
    printing = PrintingIdentifier(game, language, sourceId),
    condition = condition,
    location = locationId?.let(::LocationId),
    notes = notes,
)
