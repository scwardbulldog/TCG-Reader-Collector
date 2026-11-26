package tcg.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.PrintingIdentifier
import tcg.domain.collection.CollectionList
import tcg.domain.collection.CollectionRow
import tcg.domain.collection.PrintingDetail
import tcg.domain.collection.PrintingDetailState

class CollectionViewModel(private val list: CollectionList) : ViewModel() {
    var search by mutableStateOf("")
        private set

    /** Null until the first load finishes. */
    var rows by mutableStateOf<List<CollectionRow>?>(null)
        private set

    private var loading: Job? = null

    fun onSearchChanged(query: String) {
        search = query
        refresh()
    }

    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch { rows = list.rows(search) }
    }
}

/**
 * One instance serves every Printing detail visit: [show] switches it to another Printing, so
 * visiting many Printings never accumulates view models.
 */
class PrintingDetailViewModel(private val detailFor: (PrintingIdentifier) -> PrintingDetail) : ViewModel() {
    var state by mutableStateOf(PrintingDetailState())
        private set

    private var detail: PrintingDetail? = null
    private var showing: Job? = null

    fun show(printing: PrintingIdentifier) {
        showing?.cancel()
        val next = detailFor(printing)
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

    fun confirmDelete() = launchOn { confirmDelete() }

    fun createLocation(name: String, onCreated: (LocationId) -> Unit) =
        launchOn { onCreated(createLocation(name).id) }

    private fun launchOn(block: suspend PrintingDetail.() -> Unit) {
        val target = detail ?: return
        viewModelScope.launch { target.block() }
    }
}
