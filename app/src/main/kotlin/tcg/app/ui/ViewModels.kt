package tcg.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tcg.domain.Game
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.Card
import tcg.domain.collection.CardView
import tcg.domain.collection.CardViewState
import tcg.domain.collection.CollectionFilter
import tcg.domain.collection.CollectionList
import tcg.domain.collection.CollectionRow
import tcg.domain.collection.CollectionSort
import tcg.domain.collection.LocationUse
import tcg.domain.collection.ReleaseProductOption
import tcg.domain.collection.PrintingDetail
import tcg.domain.collection.PrintingDetailState
import java.time.LocalDate
import java.time.ZoneId

class CollectionViewModel(private val list: CollectionList) : ViewModel() {
    var search by mutableStateOf("")
        private set

    var filter by mutableStateOf(CollectionFilter())
        private set

    /** Null until the first load finishes. */
    var rows by mutableStateOf<List<CollectionRow>?>(null)
        private set

    var releaseProducts by mutableStateOf<List<ReleaseProductOption>>(emptyList())
        private set

    var locations by mutableStateOf<List<Location>>(emptyList())
        private set

    var sort by mutableStateOf(CollectionSort.BaseNumber)
        private set

    /** When the oldest stored Catalog file was generated, in the phone's time zone; null if unknown. */
    var catalogDate by mutableStateOf<LocalDate?>(null)
        private set

    /** The Locations with their copy counts while Manage Locations is open; null when it is closed. */
    var managedLocations by mutableStateOf<List<LocationUse>?>(null)
        private set

    private var loading: Job? = null

    /** True while the list is being (re)loaded, including the first load. */
    var isLoading by mutableStateOf(false)
        private set

    fun onSearchChanged(query: String) {
        search = query
        refresh()
    }

    fun onFilterChanged(changed: CollectionFilter) {
        filter = changed
        refresh()
    }

    fun onSortChanged(changed: CollectionSort) {
        sort = changed
        refresh()
    }

    fun openManageLocations() {
        viewModelScope.launch { managedLocations = list.locationUses() }
    }

    fun closeManageLocations() {
        managedLocations = null
    }

    fun renameLocation(id: LocationId, name: String) = changeLocations { list.renameLocation(id, name) }

    fun deleteLocation(id: LocationId) {
        // A deleted Location can no longer filter the list.
        if (filter.location == id) filter = filter.copy(location = null)
        changeLocations { list.deleteLocation(id) }
    }

    private fun changeLocations(change: suspend () -> Unit) {
        viewModelScope.launch {
            change()
            managedLocations = list.locationUses()
            refresh()
        }
    }

    fun refresh() {
        loading?.cancel()
        isLoading = true
        val job = viewModelScope.launch {
            releaseProducts = list.releaseProducts()
            locations = list.locations()
            catalogDate = list.catalogGeneratedAt()?.atZone(ZoneId.systemDefault())?.toLocalDate()
            rows = list.rows(search, filter, sort)
        }
        loading = job
        job.invokeOnCompletion { if (loading === job) isLoading = false }
    }
}

/** Loads the read-only Card view of one Card. */
class CardViewViewModel(private val view: CardView) : ViewModel() {
    /** Null until loaded, and when no Printing has the Card's Base Number any more. */
    var state by mutableStateOf<CardViewState?>(null)
        private set

    /** True from [show] until the Card has loaded, so "not loaded yet" and "gone" look different. */
    var isLoading by mutableStateOf(false)
        private set

    private var showing: Job? = null

    fun show(game: Game, baseNumber: String) {
        showing?.cancel()
        isLoading = true
        val job = viewModelScope.launch { state = view.of(game, baseNumber) }
        showing = job
        job.invokeOnCompletion { if (showing === job) isLoading = false }
    }
}

/**
 * One instance serves every Printing detail visit: [show] switches it to another Printing, so
 * visiting many Printings never accumulates view models.
 */
class PrintingDetailViewModel(private val detailFor: (PrintingIdentifier, LocationId?) -> PrintingDetail) : ViewModel() {
    var state by mutableStateOf(PrintingDetailState())
        private set

    private var detail: PrintingDetail? = null
    private var showing: Job? = null

    fun show(printing: PrintingIdentifier, location: LocationId? = null) {
        showing?.cancel()
        val next = detailFor(printing, location)
        detail = next
        state = PrintingDetailState()
        showing = viewModelScope.launch {
            next.load()
            next.state.collect { state = it }
        }
    }

    // Writes go to the Printing that was showing when called, so a save fired while leaving still lands.
    fun save(copy: OwnedCopy) = launchOn { save(copy) }

    fun requestDelete(id: OwnedCopyId) {
        detail?.requestDelete(id)
    }

    fun addAnotherCopy() = launchOn { addAnotherCopy() }

    fun cancelDelete() {
        detail?.cancelDelete()
    }

    fun confirmDelete(alsoDeleteCustomPrinting: Boolean = false) = launchOn { confirmDelete(alsoDeleteCustomPrinting) }

    fun rename(label: String) = launchOn { rename(label) }

    /** Moves one Owned Copy to [target], then calls [onMoved] so the caller can open the target's detail. */
    fun move(id: OwnedCopyId, target: PrintingIdentifier, onMoved: (PrintingIdentifier) -> Unit) =
        launchOn {
            move(id, target)
            onMoved(target)
        }

    fun moveToNewCustom(id: OwnedCopyId, baseNumber: String, label: String, onMoved: (PrintingIdentifier) -> Unit) =
        launchOn { onMoved(moveToNewCustom(id, baseNumber, label)) }

    suspend fun ownCard(): CardViewState? = detail?.ownCard()

    suspend fun searchCards(query: String): List<Card> = detail?.searchCards(query).orEmpty()

    fun showCard(baseNumber: String, onLoaded: (CardViewState?) -> Unit) {
        val target = detail ?: return
        viewModelScope.launch { onLoaded(target.cardView(target.printing.game, baseNumber)) }
    }

    fun createLocation(name: String, onCreated: (LocationId) -> Unit) =
        launchOn { onCreated(createLocation(name).id) }

    private fun launchOn(block: suspend PrintingDetail.() -> Unit) {
        val target = detail ?: return
        viewModelScope.launch { target.block() }
    }
}
