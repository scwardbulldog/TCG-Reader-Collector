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
    /** The Printing's details, which are the last-known ones when [noLongerInCatalog]; null for an Unresolved Printing. */
    val details: Printing? = null,
    val copies: List<OwnedCopy> = emptyList(),
    /** How many Owned Copies the Printing has in all, whatever the Location filter hides. */
    val totalCopies: Int = 0,
    val locations: List<Location> = emptyList(),
    /** The Owned Copy awaiting delete confirmation; nothing is deleted until [PrintingDetail.confirmDelete]. */
    val pendingDelete: OwnedCopyId? = null,
) {
    /** Loaded, but the Catalog has never had this Printing: only its identifier is known. */
    val unresolved: Boolean get() = loaded && details == null

    /** A Catalog refresh dropped this Printing, so [details] are the last-known ones. */
    val noLongerInCatalog: Boolean get() = details?.noLongerInCatalog == true
}

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
    private val printings = PrintingResolver(collection, catalog)
    private val customPrintings = CustomPrintings(collection)
    val state: StateFlow<PrintingDetailState> = mutableState.asStateFlow()
    private var shown: Set<OwnedCopyId> = emptySet()

    suspend fun load() {
        val all = collection.ownedCopies(printing)
        val copies = all.filter { location == null || it.location == location }
        shown = copies.map { it.id }.toSet()
        mutableState.value = PrintingDetailState(
            loaded = true,
            details = printings.resolve(printing),
            copies = copies,
            totalCopies = all.size,
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

    /** Deletes the pending Owned Copy; with [alsoDeleteCustomPrinting], a Custom Printing left with no copies goes too. */
    suspend fun confirmDelete(alsoDeleteCustomPrinting: Boolean = false) {
        val id = mutableState.value.pendingDelete ?: return
        collection.delete(id)
        if (alsoDeleteCustomPrinting) customPrintings.delete(printing)
        mutableState.value = mutableState.value.copy(pendingDelete = null)
        reloadCopies()
    }

    /** Renames a Custom Printing; the new label shows on every Owned Copy of it. */
    suspend fun rename(label: String) {
        val renamed = customPrintings.rename(printing, label) ?: return
        mutableState.value = mutableState.value.copy(details = renamed)
    }

    suspend fun createLocation(name: String): Location {
        val location = collection.createLocation(name)
        mutableState.value = mutableState.value.copy(locations = collection.locations())
        return location
    }

    private suspend fun reloadCopies() {
        val all = collection.ownedCopies(printing)
        mutableState.value = mutableState.value.copy(
            copies = all.filter { location == null || it.id in shown },
            totalCopies = all.size,
        )
    }
}
