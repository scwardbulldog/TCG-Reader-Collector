package tcg.domain.collection

import tcg.domain.CollectionStore
import tcg.domain.DeleteLocationResult
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.PrintingIdentifier

class InMemoryCollectionStore : CollectionStore {
    private val copies = mutableListOf<OwnedCopy>()
    private val locations = mutableListOf<Location>()
    private var nextId = 1L

    override suspend fun add(copy: NewOwnedCopy): OwnedCopy =
        OwnedCopy(OwnedCopyId(nextId++), copy.printing, copy.condition, copy.location, copy.notes).also(copies::add)

    override suspend fun addAll(copies: List<NewOwnedCopy>): List<OwnedCopy> = copies.map { add(it) }

    override suspend fun ownedCopies(printing: PrintingIdentifier): List<OwnedCopy> =
        copies.filter { it.printing == printing }

    override suspend fun update(copy: OwnedCopy) {
        val index = copies.indexOfFirst { it.id == copy.id }
        if (index >= 0) copies[index] = copy
    }

    override suspend fun delete(id: OwnedCopyId) {
        copies.removeAll { it.id == id }
    }

    override suspend fun countsByPrinting(): Map<PrintingIdentifier, Int> =
        copies.groupingBy { it.printing }.eachCount()

    override suspend fun createLocation(name: String): Location =
        Location(LocationId(nextId++), name).also(locations::add)

    override suspend fun locations(): List<Location> = locations.toList()

    override suspend fun renameLocation(id: LocationId, name: String) {
        val index = locations.indexOfFirst { it.id == id }
        if (index >= 0) locations[index] = Location(id, name)
    }

    override suspend fun deleteLocation(id: LocationId): DeleteLocationResult {
        if (copies.any { it.location == id }) return DeleteLocationResult.NotEmpty
        locations.removeAll { it.id == id }
        return DeleteLocationResult.Deleted
    }
}
