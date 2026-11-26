package tcg.domain.collection

import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
import tcg.domain.DeleteLocationResult
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.NewOwnedCopy
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

class InMemoryCollectionStore : CollectionStore {
    private val copies = mutableListOf<OwnedCopy>()
    private val locations = mutableListOf<Location>()
    private val lastKnown = mutableMapOf<PrintingIdentifier, Printing>()
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

    override suspend fun rememberPrintings(printings: List<Printing>) {
        printings.forEach { lastKnown[it.identifier] = it }
    }

    override suspend fun lastKnownPrinting(identifier: PrintingIdentifier): Printing? = lastKnown[identifier]

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

    override suspend fun snapshot(): CollectionSnapshot = CollectionSnapshot(locations.toList(), copies.toList())

    override suspend fun replaceCollection(snapshot: CollectionSnapshot) {
        copies.clear()
        copies.addAll(snapshot.copies)
        locations.clear()
        locations.addAll(snapshot.locations)
        lastKnown.clear()
        nextId = maxOf(nextId, (snapshot.copies.map { it.id.value } + snapshot.locations.map { it.id.value }).maxOrNull()?.plus(1) ?: 1)
    }
}
