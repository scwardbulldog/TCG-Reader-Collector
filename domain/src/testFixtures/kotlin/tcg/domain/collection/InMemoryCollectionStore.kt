package tcg.domain.collection

import tcg.domain.CollectionSnapshot
import tcg.domain.CollectionStore
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
import java.time.Instant

class InMemoryCollectionStore(private val clock: () -> Instant = Instant::now) : CollectionStore {
    private val copies = mutableListOf<OwnedCopy>()
    private val locations = mutableListOf<Location>()
    private val custom = mutableMapOf<PrintingIdentifier, Printing>()
    private val lastKnown = mutableMapOf<PrintingIdentifier, Printing>()
    private var nextId = 1L

    override suspend fun add(copy: NewOwnedCopy): OwnedCopy =
        OwnedCopy(OwnedCopyId(nextId++), copy.printing, copy.condition, copy.location, copy.notes, clock()).also(copies::add)

    override suspend fun addAll(copies: List<NewOwnedCopy>): List<OwnedCopy> = copies.map { add(it) }

    override suspend fun ownedCopies(printing: PrintingIdentifier): List<OwnedCopy> =
        copies.filter { it.printing == printing }

    override suspend fun update(copy: OwnedCopy) {
        val index = copies.indexOfFirst { it.id == copy.id }
        if (index >= 0) copies[index] = copy.copy(printing = copies[index].printing, addedAt = copies[index].addedAt)
    }

    override suspend fun move(id: OwnedCopyId, printing: PrintingIdentifier) {
        val index = copies.indexOfFirst { it.id == id }
        if (index >= 0) copies[index] = copies[index].copy(printing = printing)
    }

    override suspend fun delete(id: OwnedCopyId) {
        copies.removeAll { it.id == id }
    }

    override suspend fun countsByPrinting(): Map<PrintingIdentifier, Int> =
        copies.groupingBy { it.printing }.eachCount()

    override suspend fun saveCustomPrinting(printing: Printing) {
        require(printing.identifier.isCustom) { "Only a Custom Printing can be saved here" }
        custom[printing.identifier] = printing
    }

    override suspend fun customPrinting(identifier: PrintingIdentifier): Printing? = custom[identifier]

    override suspend fun customPrintings(game: Game, language: Language): List<Printing> =
        custom.values.filter { it.identifier.game == game && it.identifier.language == language }

    override suspend fun deleteCustomPrinting(identifier: PrintingIdentifier) {
        custom.remove(identifier)
    }

    override suspend fun createLocation(name: String): Location =
        Location(LocationId(nextId++), name).also(locations::add)

    override suspend fun locations(): List<Location> = locations.toList()

    override suspend fun renameLocation(id: LocationId, name: String) {
        val index = locations.indexOfFirst { it.id == id }
        if (index >= 0) locations[index] = Location(id, name)
    }

    override suspend fun deleteLocation(id: LocationId) {
        copies.replaceAll { if (it.location == id) it.copy(location = null) else it }
        locations.removeAll { it.id == id }
    }

    override suspend fun copiesIn(id: LocationId): Int = copies.count { it.location == id }

    override suspend fun snapshot(): CollectionSnapshot =
        CollectionSnapshot(locations.toList(), copies.toList(), custom.values.toList(), lastKnown.values.toList())

    override suspend fun replaceCollection(snapshot: CollectionSnapshot) {
        copies.clear()
        copies.addAll(snapshot.copies)
        locations.clear()
        locations.addAll(snapshot.locations)
        custom.clear()
        snapshot.customPrintings.forEach { custom[it.identifier] = it }
        lastKnown.clear()
        snapshot.lastKnownPrintings.forEach { lastKnown[it.identifier] = it }
        nextId = maxOf(nextId, (snapshot.copies.map { it.id.value } + snapshot.locations.map { it.id.value }).maxOrNull()?.plus(1) ?: 1)
    }
}
