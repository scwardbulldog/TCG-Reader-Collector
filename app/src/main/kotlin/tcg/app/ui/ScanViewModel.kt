package tcg.app.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tcg.app.scanning.CachePhotoSource
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.Printing
import tcg.domain.scanning.BaseNumberLocker
import tcg.domain.scanning.BaseNumberRead
import tcg.domain.scanning.Batch
import tcg.domain.scanning.BatchRow
import tcg.domain.scanning.BatchRowId
import tcg.domain.scanning.BulkCapture
import tcg.domain.scanning.BulkStatus
import tcg.domain.scanning.PhotoId
import tcg.domain.scanning.ScanSessionInput
import tcg.domain.scanning.ScanLookup
import tcg.domain.scanning.ScanSetup
import java.io.File

/** The Printings offered for one lookup, with the collector's pick, copy count and one-off condition. */
data class Confirmation(val candidates: List<Printing>, val selected: Printing?, val count: Int = 1, val condition: Condition? = null)

/** Single confirms each scan; Bulk snaps a stack of cards and reviews them once. */
enum class ScanMode { Single, Bulk }

/** A "Discard N unsaved cards?" question; [then] is what the collector was doing when it came up. */
data class PendingDiscard(val then: Then, val cards: Int) {
    enum class Then { SwitchToSingle, Leave }
}

/** What the input screen is showing after the last lookup or search. */
sealed interface ScanInputResult {
    data object None : ScanInputResult
    data class NoMatch(val baseNumber: String) : ScanInputResult
    data class SearchResults(val printings: List<Printing>) : ScanInputResult
}

class ScanViewModel(
    private val catalog: CatalogStore,
    private val collection: CollectionStore,
    private val photos: CachePhotoSource,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private var locker = BaseNumberLocker()
    private var bulk: BulkCapture? = null
    private var bulkStatusJob: Job? = null
    private var bulkRowsJob: Job? = null

    var setup by mutableStateOf(ScanSetup())
        private set
    var locations by mutableStateOf<List<Location>>(emptyList())
        private set

    /** Non-null once the collector has started a session; null means the setup screen. */
    var sessionInput by mutableStateOf<ScanSessionInput?>(null)
        private set
    var mode by mutableStateOf(ScanMode.Single)
        private set

    /** Bulk Add progress; all zero in Single mode. */
    var bulkStatus by mutableStateOf(BulkStatus())
        private set

    /** The Bulk Add review list: failed rows first, then ambiguous, then resolved. */
    var bulkRows by mutableStateOf<List<BatchRow>>(emptyList())
        private set

    /** True while the review list replaces the camera. */
    var reviewing by mutableStateOf(false)
        private set

    /** Cards snapped but not yet added: photos still being read plus everything in the review list. */
    val unsavedCards: Int get() = (bulkStatus.snapped - bulkStatus.read) + bulkRows.sumOf { it.count }

    /** Set while "Discard N unsaved cards?" waits for the collector's answer. */
    var pendingDiscard by mutableStateOf<PendingDiscard?>(null)
        private set
    var query by mutableStateOf("")
        private set
    var result by mutableStateOf<ScanInputResult>(ScanInputResult.None)
        private set
    var confirmation by mutableStateOf<Confirmation?>(null)
        private set
    private var justCreated: Printing? = null

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

    /**
     * Asks to leave the session. True means it ended; false means unsaved Bulk Add cards need the
     * collector's answer first (see [pendingDiscard]).
     */
    fun requestEnd(): Boolean {
        if (unsavedCards > 0) {
            pendingDiscard = PendingDiscard(PendingDiscard.Then.Leave, unsavedCards)
            return false
        }
        endSession()
        return true
    }

    /** Ends the session; a different Language means a new session, so setup keeps nothing of Language. */
    fun endSession() {
        closeBulk()
        mode = ScanMode.Single
        sessionInput = null
        setup = setup.copy(language = null)
        clearInput()
    }

    fun selectMode(selected: ScanMode) {
        val input = sessionInput ?: return
        if (selected == mode) return
        when (selected) {
            ScanMode.Bulk -> {
                val capture = BulkCapture(Batch(input), photos, viewModelScope)
                bulk = capture
                bulkStatusJob = viewModelScope.launch { capture.status.collect { bulkStatus = it } }
                bulkRowsJob = viewModelScope.launch {
                    var withThumbnails = emptySet<PhotoId>()
                    capture.rows.collect { rows ->
                        bulkRows = rows
                        val failed = rows.filterIsInstance<BatchRow.Failed>().map { it.photo }.toSet()
                        (withThumbnails - failed).forEach(photos::dropThumbnail)
                        withThumbnails = failed
                    }
                }
                mode = ScanMode.Bulk
            }
            ScanMode.Single -> {
                if (unsavedCards > 0) {
                    pendingDiscard = PendingDiscard(PendingDiscard.Then.SwitchToSingle, unsavedCards)
                } else {
                    leaveBulk()
                }
            }
        }
    }

    fun showReview() {
        reviewing = true
    }

    fun hideReview() {
        reviewing = false
    }

    fun pickPrinting(row: BatchRowId, printing: Printing) {
        val capture = bulk ?: return
        viewModelScope.launch { capture.pick(row, printing) }
    }

    fun enterBaseNumber(row: BatchRowId, baseNumber: String) {
        val capture = bulk ?: return
        viewModelScope.launch { capture.enterBaseNumber(row, baseNumber) }
    }

    fun removeRow(row: BatchRowId) {
        val capture = bulk ?: return
        viewModelScope.launch { capture.remove(row) }
    }

    /** Adds the resolved rows to the Collection; failed and ambiguous rows stay for the collector. */
    fun addAll() {
        val capture = bulk ?: return
        viewModelScope.launch {
            val added = capture.addAll()
            lastAdded = "Added ${added.size} ${if (added.size == 1) "card" else "cards"} from Bulk Add"
            if (capture.rows.value.isEmpty()) reviewing = false
        }
    }

    /** Throws the batch away. True if the collector was leaving the session, so the caller should close the screen. */
    fun confirmDiscard(): Boolean {
        val pending = pendingDiscard ?: return false
        pendingDiscard = null
        leaveBulk()
        return pending.then == PendingDiscard.Then.Leave
    }

    fun keepBatch() {
        pendingDiscard = null
    }

    /** Where the camera should write the next Bulk Add photo. */
    fun newPhoto(): Pair<PhotoId, File> = photos.newPhoto()

    /** The in-memory preview of a failed row's photo, if it is still held. */
    fun thumbnail(photo: PhotoId): Bitmap? = photos.thumbnails[photo]

    fun onPhotoSaved(photo: PhotoId) {
        val capture = bulk
        if (capture == null) photos.release(photo) else capture.snap(photo)
    }

    fun onPhotoFailed(photo: PhotoId) = photos.release(photo)

    private fun leaveBulk() {
        closeBulk()
        mode = ScanMode.Single
    }

    private fun closeBulk() {
        bulkStatusJob?.cancel()
        bulkStatusJob = null
        bulkRowsJob?.cancel()
        bulkRowsJob = null
        bulk?.close()
        bulk = null
        photos.dropThumbnails()
        bulkStatus = BulkStatus()
        bulkRows = emptyList()
        reviewing = false
        pendingDiscard = null
    }

    override fun onCleared() = closeBulk()

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

    /** Creates a Custom Printing for a no-match and proposes it for confirmation like any other Printing. */
    fun createCustomPrinting(baseNumber: String, label: String) {
        val current = sessionInput ?: return
        viewModelScope.launch {
            val printing = current.createCustomPrinting(baseNumber, label)
            justCreated = printing
            result = ScanInputResult.None
            confirmation = Confirmation(listOf(printing), selected = printing)
        }
    }

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
        discardUnusedCustomPrinting()
        confirmation = null
        finishVerdict()
    }

    // A Custom Printing made for a no-match and then not added would otherwise linger with no way to delete it.
    private fun discardUnusedCustomPrinting() {
        val created = justCreated ?: return
        justCreated = null
        val current = sessionInput ?: return
        viewModelScope.launch { current.discardCustomPrinting(created) }
    }

    fun confirm() {
        val current = sessionInput ?: return
        val pending = confirmation ?: return
        val printing = pending.selected ?: return
        viewModelScope.launch {
            val added = current.add(printing, pending.count, pending.condition ?: current.session.defaultCondition)
            justCreated = null
            lastAdded = "Added ${added.size} × ${printing.name} (${printing.baseNumber})"
            clearInput()
        }
    }

    private fun clearInput() {
        justCreated = null
        query = ""
        result = ScanInputResult.None
        confirmation = null
        finishVerdict()
    }

    /** The collector dealt with the last lock: added, cancelled or skipped it. */
    private fun finishVerdict() = locker.verdict(clock())
}
