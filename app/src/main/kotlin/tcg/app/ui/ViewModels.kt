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
import tcg.domain.collection.CollectionList
import tcg.domain.collection.CollectionRow
import tcg.domain.collection.PrintingDetail

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

class PrintingDetailViewModel(private val detail: PrintingDetail) : ViewModel() {
    val state = detail.state

    init {
        viewModelScope.launch { detail.load() }
    }

    fun save(copy: OwnedCopy) {
        viewModelScope.launch { detail.save(copy) }
    }

    fun requestDelete(id: OwnedCopyId) = detail.requestDelete(id)

    fun cancelDelete() = detail.cancelDelete()

    fun confirmDelete() {
        viewModelScope.launch { detail.confirmDelete() }
    }

    fun createLocation(name: String, onCreated: (LocationId) -> Unit) {
        viewModelScope.launch { onCreated(detail.createLocation(name).id) }
    }
}
