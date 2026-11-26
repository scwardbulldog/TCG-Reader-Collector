package tcg.domain.collection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

data class PrintingDetailState(
    /** False until [PrintingDetail.load] finishes, so an empty [copies] can be told apart from "not loaded yet". */
    val loaded: Boolean = false,
    val catalogEntry: Printing? = null,
    val copies: List<OwnedCopy> = emptyList(),
    val locations: List<Location> = emptyList(),
    /** The Owned Copy awaiting delete confirmation; nothing is deleted until [PrintingDetail.confirmDelete]. */
    val pendingDelete: OwnedCopyId? = null,
)

/**
 * Maintains the individual Owned Copies of one Printing. With a [location] filter only the copies kept
 * there are shown, and a copy edited out of that Location stays visible until the detail is reloaded.
 */
class PrintingDetail(
    val printing: PrintingIdentifier,
    private val collection: CollectionStore,
    private val catalog: CatalogStore,
    private val location: LocationId? = null,
) {
    private val mutableState = MutableStateFlow(PrintingDetailState())
    val state: StateFlow<PrintingDetailState> = mutableState.asStateFlow()
    private var shown: Set<OwnedCopyId> = emptySet()

    suspend fun load() {
        val copies = collection.ownedCopies(printing).filter { location == null || it.location == location }
        shown = copies.map { it.id }.toSet()
        mutableState.value = PrintingDetailState(
            loaded = true,
            catalogEntry = catalog.find(printing),
            copies = copies,
            locations = collection.locations(),
        )
    }

    suspend fun save(copy: OwnedCopy) {
        collection.update(copy)
        reloadCopies()
    }

    fun requestDelete(id: OwnedCopyId) {
        mutableState.value = mutableState.value.copy(pendingDelete = id)
    }

    fun cancelDelete() {
        mutableState.value = mutableState.value.copy(pendingDelete = null)
    }

    suspend fun confirmDelete() {
        val id = mutableState.value.pendingDelete ?: return
        collection.delete(id)
        mutableState.value = mutableState.value.copy(pendingDelete = null)
        reloadCopies()
    }

    suspend fun createLocation(name: String): Location {
        val location = collection.createLocation(name)
        mutableState.value = mutableState.value.copy(locations = collection.locations())
        return location
    }

    private suspend fun reloadCopies() {
        mutableState.value = mutableState.value.copy(
            copies = collection.ownedCopies(printing).filter { location == null || it.id in shown },
        )
    }
}
