package tcg.app.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.Printing
import tcg.domain.scanning.BaseNumberLocker
import tcg.domain.scanning.BaseNumberRead
import tcg.domain.scanning.ScanSessionInput
import tcg.domain.scanning.ScanLookup
import tcg.domain.scanning.ScanSetup

/** The Printings offered for one lookup, with the collector's pick, copy count and one-off condition. */
data class Confirmation(val candidates: List<Printing>, val selected: Printing?, val count: Int = 1, val condition: Condition? = null)

/** What the input screen is showing after the last lookup or search. */
sealed interface ScanInputResult {
    data object None : ScanInputResult
    data class NoMatch(val baseNumber: String) : ScanInputResult
    data class SearchResults(val printings: List<Printing>) : ScanInputResult
}

class ScanViewModel(
    private val catalog: CatalogStore,
    private val collection: CollectionStore,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private var locker = BaseNumberLocker()

    var setup by mutableStateOf(ScanSetup())
        private set
    var locations by mutableStateOf<List<Location>>(emptyList())
        private set

    /** Non-null once the collector has started a session; null means the setup screen. */
    var sessionInput by mutableStateOf<ScanSessionInput?>(null)
        private set
    var query by mutableStateOf("")
        private set
    var result by mutableStateOf<ScanInputResult>(ScanInputResult.None)
        private set
    var confirmation by mutableStateOf<Confirmation?>(null)
        private set

    /** The last add, shown on the input screen so the collector sees it landed. */
    var lastAdded by mutableStateOf<String?>(null)
        private set

    fun loadLocations() {
        viewModelScope.launch { locations = collection.locations() }
    }

    fun chooseLanguage(language: Language) {
        setup = setup.copy(language = language)
    }

    fun chooseLocation(location: Location) {
        setup = setup.copy(location = location)
    }

    fun chooseCondition(condition: Condition) {
        setup = setup.copy(condition = condition)
    }

    fun createLocation(name: String) {
        viewModelScope.launch {
            val created = collection.createLocation(name)
            locations = collection.locations()
            chooseLocation(created)
        }
    }

    fun start() {
        val session = setup.start() ?: return
        sessionInput = ScanSessionInput(session, catalog, collection)
        locker = BaseNumberLocker()
        clearInput()
        lastAdded = null
    }

    /** Ends the session; a different Language means a new session, so setup keeps nothing of Language. */
    fun endSession() {
        sessionInput = null
        setup = setup.copy(language = null)
        clearInput()
    }

    fun onQueryChanged(text: String) {
        query = text
    }

    fun lookUp() {
        val current = sessionInput ?: return
        viewModelScope.launch { show(current.lookup(query)) }
    }

    /**
     * A Base Number read by the camera. Reads count as votes; a locked number only fills the text field,
     * so the collector sees the finding and taps Look up themselves.
     */
    fun onRead(read: BaseNumberRead) {
        if (sessionInput == null || confirmation != null) return
        query = locker.read(read.baseNumber, clock()) ?: return
    }

    private fun show(lookup: ScanLookup) {
        when (lookup) {
            is ScanLookup.Candidates -> {
                result = ScanInputResult.None
                confirmation = Confirmation(lookup.printings, lookup.preselected)
            }
            is ScanLookup.NoMatch -> result = ScanInputResult.NoMatch(lookup.baseNumber)
        }
    }

    fun search() {
        val current = sessionInput ?: return
        viewModelScope.launch { result = ScanInputResult.SearchResults(current.search(query)) }
    }

    fun dismissSearch() {
        result = ScanInputResult.None
    }

    /** Dismisses the no-match message and keeps the text so the collector can edit it and search. */
    fun searchManually() {
        result = ScanInputResult.None
        finishVerdict()
    }

    /** Gives up on a no-match: nothing is added. */
    fun skip() = clearInput()

    fun openSearchResult(printing: Printing) {
        confirmation = Confirmation(listOf(printing), selected = printing)
    }

    fun select(printing: Printing) {
        confirmation = confirmation?.copy(selected = printing)
    }

    fun setCount(count: Int) {
        confirmation = confirmation?.copy(count = count.coerceAtLeast(1))
    }

    fun overrideCondition(condition: Condition) {
        confirmation = confirmation?.copy(condition = condition)
    }

    fun cancelConfirmation() {
        confirmation = null
        finishVerdict()
    }

    fun confirm() {
        val current = sessionInput ?: return
        val pending = confirmation ?: return
        val printing = pending.selected ?: return
        viewModelScope.launch {
            val added = current.add(printing, pending.count, pending.condition ?: current.session.defaultCondition)
            lastAdded = "Added ${added.size} × ${printing.name} (${printing.baseNumber})"
            clearInput()
        }
    }

    private fun clearInput() {
        query = ""
        result = ScanInputResult.None
        confirmation = null
        finishVerdict()
    }

    /** The collector dealt with the last lock: added, cancelled or skipped it. */
    private fun finishVerdict() = locker.verdict(clock())
}
