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
import tcg.app.scanning.ScanDebugLog
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.OwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.scanning.BaseNumberLocker
import tcg.domain.scanning.BaseNumberRead
import tcg.domain.scanning.Batch
import tcg.domain.scanning.BatchRow
import tcg.domain.scanning.BatchRowId
import tcg.domain.scanning.BulkCapture
import tcg.domain.scanning.BulkStatus
import tcg.domain.scanning.CardMatch
import tcg.domain.scanning.LanguageStore
import tcg.domain.scanning.PhotoId
import tcg.domain.scanning.ScanLookup
import tcg.domain.scanning.ScanSearch
import tcg.domain.scanning.ScanSession
import tcg.domain.scanning.ScanSessionInput
import java.io.File

/** The Printings offered for one lookup, with the collector's pick, copy count, one-off condition and Location. */
data class Confirmation(
    val candidates: List<Printing>,
    val selected: Printing?,
    val location: Location? = null,
    val count: Int = 1,
    val condition: Condition = Condition.NearMint,
)

/** The "Added …" message and the Owned Copies that add created. */
data class LastAdded(val message: String, val copies: List<OwnedCopy>)

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
    data class SearchResults(val cards: List<CardMatch>) : ScanInputResult
}

class ScanViewModel(
    private val catalog: CatalogStore,
    private val collection: CollectionStore,
    private val photos: CachePhotoSource,
    private val languages: LanguageStore,
    private val debugLog: ScanDebugLog? = null,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private var locker = BaseNumberLocker()
    private var bulk: BulkCapture? = null
    private var bulkStatusJob: Job? = null
    private var bulkRowsJob: Job? = null
    private var lookupJob: Job? = null

    /** Scanning starts straight away; leaving the screen ([endSession]) begins a fresh session. */
    var sessionInput by mutableStateOf(newSessionInput())
        private set

    /** The session's current Language, mirrored here so the screen recomposes when it is switched. */
    var language by mutableStateOf(sessionInput.session.language)
        private set
    var locations by mutableStateOf<List<Location>>(emptyList())
        private set

    /** The Location "Add all" will use; starts as the session's last used Location. */
    var bulkLocation by mutableStateOf<Location?>(null)
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

    /** The last add, shown on the input screen so the collector sees it landed, with the copies [undoLastAdd] removes. */
    var lastAdded by mutableStateOf<LastAdded?>(null)
        private set

    /** How many Owned Copies the Collection holds of each Printing in the open confirmation. */
    var owned by mutableStateOf<Map<PrintingIdentifier, Int>>(emptyMap())
        private set

    fun loadOwned() {
        val candidates = confirmation?.candidates ?: return
        val current = sessionInput
        viewModelScope.launch { owned = current.ownedCounts(candidates) }
    }

    /** Removes the copies the last add created. */
    fun undoLastAdd() {
        val last = lastAdded ?: return
        val current = sessionInput
        lastAdded = null
        viewModelScope.launch { current.undo(last.copies) }
    }

    fun loadLocations() {
        viewModelScope.launch { locations = collection.locations() }
    }

    /**
     * Switches the session's Language. An open confirmation, no-match or search is redone in the new
     * Language, so the collector doesn't rescan. Ignored while a Bulk Add batch holds unsaved cards,
     * since a batch uses one Language.
     */
    fun switchLanguage(selected: Language) {
        if (selected == language || unsavedCards > 0) return
        sessionInput.session.switchLanguage(selected)
        language = selected
        lookupJob?.cancel() // anything still in flight was looked up in the old Language
        val open = confirmation
        val missing = result as? ScanInputResult.NoMatch
        when {
            open != null -> relookUp(open.candidates.first().baseNumber)
            missing != null -> relookUp(missing.baseNumber)
            result is ScanInputResult.SearchResults -> search()
        }
    }

    // The open confirmation keeps its count, condition and Location; a Custom Printing made in the old Language is dropped.
    private fun relookUp(baseNumber: String) {
        discardUnusedCustomPrinting()
        val current = sessionInput
        launchLookup {
            when (val lookup = current.lookup(baseNumber)) {
                is ScanLookup.Candidates -> {
                    result = ScanInputResult.None
                    confirmation = (confirmation ?: confirmationOf(lookup.printings, lookup.preselected))
                        .copy(candidates = lookup.printings, selected = lookup.preselected)
                }
                is ScanLookup.NoMatch -> {
                    confirmation = null
                    result = ScanInputResult.NoMatch(lookup.baseNumber)
                }
            }
        }
    }

    fun chooseLocation(location: Location?) {
        confirmation = confirmation?.copy(location = location)
    }

    fun chooseBulkLocation(location: Location?) {
        bulkLocation = location
    }

    /** Creates a Location and picks it for the open confirmation, or for "Add all" when [forBatch]. */
    fun createLocation(name: String, forBatch: Boolean = false) {
        viewModelScope.launch {
            val created = collection.createLocation(name)
            locations = collection.locations()
            if (forBatch) bulkLocation = created else confirmation = confirmation?.copy(location = created)
        }
    }

    private fun newSessionInput() = ScanSessionInput(ScanSession(Game.OnePiece, languages), catalog, collection)

    /** True while a lookup, search or re-lookup is running, so the screen can show it is working. */
    var searching by mutableStateOf(false)
        private set

    // One lookup, search or re-lookup at a time: a newer one, a switch or leaving the input cancels the older.
    private fun launchLookup(block: suspend () -> Unit) {
        lookupJob?.cancel()
        searching = true
        val job = viewModelScope.launch { block() }
        lookupJob = job
        // A cancelled older job must not switch off the newer one's indicator.
        job.invokeOnCompletion { if (lookupJob === job) searching = false }
    }

    private fun confirmationOf(candidates: List<Printing>, selected: Printing?) =
        Confirmation(candidates, selected, location = sessionInput.session.location)

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

    /** Ends the session; the next one starts fresh, in the remembered Language and with no Location. */
    fun endSession() {
        closeBulk()
        mode = ScanMode.Single
        sessionInput = newSessionInput()
        language = sessionInput.session.language
        bulkLocation = null
        locker = BaseNumberLocker()
        clearInput()
        lastAdded = null
    }

    fun selectMode(selected: ScanMode) {
        val input = sessionInput
        if (selected == mode) return
        lastAdded = null
        when (selected) {
            ScanMode.Bulk -> {
                bulkLocation = input.session.location
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

    /** The Cards offered for each failed row fixed by name; an empty list means the text matched nothing. */
    var rowMatches by mutableStateOf<Map<BatchRowId, List<CardMatch>>>(emptyMap())
        private set

    /** Fixes a failed row from a Base Number or a name; a name lists Cards in [rowMatches] to choose from. */
    fun fixRow(row: BatchRowId, text: String) {
        val capture = bulk ?: return
        viewModelScope.launch { rowMatches = rowMatches + (row to capture.fix(row, text)) }
    }

    fun removeRow(row: BatchRowId) {
        val capture = bulk ?: return
        viewModelScope.launch { capture.remove(row) }
    }

    /** Adds the resolved rows to the Collection; failed and ambiguous rows stay for the collector. */
    fun addAll() {
        val capture = bulk ?: return
        viewModelScope.launch {
            val added = capture.addAll(bulkLocation)
            lastAdded = LastAdded("Added ${added.size} ${if (added.size == 1) "card" else "cards"} from Bulk Add", added)
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
        lastAdded = null
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
        rowMatches = emptyMap()
        pendingDiscard = null
    }

    override fun onCleared() = closeBulk()

    fun onQueryChanged(text: String) {
        query = text
        if (result is ScanInputResult.NoMatch) result = ScanInputResult.None
    }

    /** Looks up a Base Number or searches by name: proposals for a Base Number, a Card list for anything else. */
    fun search() {
        val current = sessionInput
        launchLookup {
            when (val found = current.find(query)) {
                is ScanSearch.Proposals -> show(found.candidates)
                is ScanSearch.Cards -> result = ScanInputResult.SearchResults(found.matches)
                is ScanSearch.NoMatch -> result = ScanInputResult.NoMatch(found.text)
            }
        }
    }

    /**
     * A Base Number read by the camera. Reads count as votes; a locked number only fills the text field,
     * so the collector sees the finding and taps Look up themselves.
     */
    fun onRead(read: BaseNumberRead) {
        if (confirmation != null) return
        debugLog?.note("language", language.name)
        debugLog?.note("votesRequired", BaseNumberLocker.DEFAULT_VOTES)
        debugLog?.note("clearGapMillis", BaseNumberLocker.DEFAULT_CLEAR_GAP_MILLIS)
        val votesBefore = locker.currentVotes()
        val locked = locker.read(read.baseNumber, clock()) ?: return
        debugLog?.onLock(locked, votesBefore + (locked to (votesBefore[locked] ?: 0) + 1))
        query = locked
    }

    private fun show(lookup: ScanLookup) {
        when (lookup) {
            is ScanLookup.Candidates -> {
                result = ScanInputResult.None
                confirmation = confirmationOf(lookup.printings, lookup.preselected)
            }
            is ScanLookup.NoMatch -> result = ScanInputResult.NoMatch(lookup.baseNumber)
        }
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

    /**
     * Creates a Custom Printing, for a no-match or when none of the proposed Printings is the collector's,
     * and offers it for confirmation next to the Printings already proposed.
     */
    fun createCustomPrinting(baseNumber: String, label: String) {
        val current = sessionInput
        val previous = justCreated
        val proposed = confirmation?.candidates.orEmpty().filter { it.identifier != previous?.identifier }
        discardUnusedCustomPrinting()
        viewModelScope.launch {
            val printing = current.createCustomPrinting(baseNumber, label)
            justCreated = printing
            result = ScanInputResult.None
            confirmation = confirmation?.copy(candidates = proposed + printing, selected = printing)
                ?: confirmationOf(listOf(printing), printing)
        }
    }

    /** Opens the proposals of a Card from the search results, which stay behind them for when the collector backs out. */
    fun openCard(card: CardMatch) {
        val current = sessionInput
        launchLookup {
            (current.lookup(card.baseNumber) as? ScanLookup.Candidates)?.let { confirmation = confirmationOf(it.printings, it.preselected) }
        }
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
        lookupJob?.cancel()
        discardUnusedCustomPrinting()
        confirmation = null
        finishVerdict()
    }

    // A Custom Printing made for a no-match and then not added would otherwise linger with no way to delete it.
    private fun discardUnusedCustomPrinting() {
        val created = justCreated ?: return
        justCreated = null
        val current = sessionInput
        viewModelScope.launch { current.discardCustomPrinting(created) }
    }

    fun confirm() {
        val current = sessionInput
        val pending = confirmation ?: return
        val printing = pending.selected ?: return
        // A switch is still re-looking up this confirmation; its Printing is from the old Language.
        if (printing.identifier.language != language) return
        viewModelScope.launch {
            val added = current.add(printing, pending.count, pending.condition, pending.location)
            if (justCreated?.identifier == printing.identifier) justCreated = null else discardUnusedCustomPrinting()
            lastAdded = LastAdded("Added ${added.size} × ${printing.name} (${printing.baseNumber})", added)
            clearInput()
        }
    }

    private fun clearInput() {
        lookupJob?.cancel()
        justCreated = null
        query = ""
        result = ScanInputResult.None
        confirmation = null
        finishVerdict()
    }

    /** The collector dealt with the last lock: added, cancelled or skipped it. */
    private fun finishVerdict() = locker.verdict(clock())
}
