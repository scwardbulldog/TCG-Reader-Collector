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
import tcg.domain.collection.CardView
import tcg.domain.collection.CardViewState
import tcg.domain.collection.CollectionFilter
import tcg.domain.collection.CollectionList
import tcg.domain.collection.CollectionRow
import tcg.domain.collection.PrintingDetail
import tcg.domain.collection.PrintingDetailState

class CollectionViewModel(private val list: CollectionList) : ViewModel() {
    var search by mutableStateOf("")
        private set

    var filter by mutableStateOf(CollectionFilter())
        private set

    /** Null until the first load finishes. */
    var rows by mutableStateOf<List<CollectionRow>?>(null)
        private set

    var releaseProducts by mutableStateOf<List<String>>(emptyList())
        private set

    var locations by mutableStateOf<List<Location>>(emptyList())
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

    fun refresh() {
        loading?.cancel()
        isLoading = true
        val job = viewModelScope.launch {
            releaseProducts = list.releaseProducts()
            locations = list.locations()
            rows = list.rows(search, filter)
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

    fun cancelDelete() {
        detail?.cancelDelete()
    }

    fun confirmDelete(alsoDeleteCustomPrinting: Boolean = false) = launchOn { confirmDelete(alsoDeleteCustomPrinting) }

    fun rename(label: String) = launchOn { rename(label) }

    fun createLocation(name: String, onCreated: (LocationId) -> Unit) =
        launchOn { onCreated(createLocation(name).id) }

    private fun launchOn(block: suspend PrintingDetail.() -> Unit) {
        val target = detail ?: return
        viewModelScope.launch { target.block() }
    }
}
