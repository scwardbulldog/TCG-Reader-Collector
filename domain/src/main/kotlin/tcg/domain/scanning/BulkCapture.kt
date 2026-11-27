package tcg.domain.scanning

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import tcg.domain.Location
import tcg.domain.OwnedCopy
import tcg.domain.Printing

/** Where snapped photos live until they are read. */
interface PhotoSource {
    /** The Base Number in [photo], or null if it has none. May throw; the read is then a failed read. */
    suspend fun baseNumberIn(photo: PhotoId): String?

    /** Throws [photo] away (its full-size file). Called exactly once per snapped photo, however the read went. */
    fun release(photo: PhotoId)
}

/** What the Bulk Add screen reports while snapping: photos taken, photos read so far, rows needing the collector's attention. */
data class BulkStatus(val snapped: Int = 0, val read: Int = 0, val needsAttention: Int = 0)

/**
 * Reads snapped photos into a [Batch] in the background, [parallelism] at a time, so [snap] never waits.
 * Every snapped photo is released exactly once: when its read ends, however it ended, or on [close].
 */
class BulkCapture(
    private val batch: Batch,
    private val source: PhotoSource,
    scope: CoroutineScope,
    parallelism: Int = 2,
) {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val work = CoroutineScope(scope.coroutineContext + job)
    private val permits = Semaphore(parallelism)
    private val lock = Mutex()
    private val unreleased = LinkedHashSet<PhotoId>()
    private val state = MutableStateFlow(BulkStatus())
    private val reviewRows = MutableStateFlow<List<BatchRow>>(emptyList())

    val status: StateFlow<BulkStatus> get() = state

    /** The review list as it stands: flagged rows first. Updated after every read and every fix. */
    val rows: StateFlow<List<BatchRow>> get() = reviewRows

    fun snap(photo: PhotoId) {
        synchronized(unreleased) { unreleased += photo }
        state.update { it.copy(snapped = it.snapped + 1) }
        work.launch {
            try {
                permits.withPermit { read(photo) }
            } finally {
                release(photo)
            }
        }
    }

    /** Resolves the ambiguous row [id] to one of its candidates. */
    suspend fun pick(id: BatchRowId, printing: Printing) = fix { batch.pick(id, printing) }

    /** Re-reads the failed row [id] from a Base Number the collector typed. */
    suspend fun enterBaseNumber(id: BatchRowId, baseNumber: String) = fix { batch.enterBaseNumber(id, baseNumber) }

    suspend fun remove(id: BatchRowId) = fix { batch.remove(id) }

    /** Adds every resolved row to the Collection in [location]; flagged rows stay for the collector. */
    suspend fun addAll(location: Location?): List<OwnedCopy> = fix { batch.addAll(location) }

    private suspend fun <T> fix(change: suspend () -> T): T = lock.withLock {
        val before = batch.rows.sumOf { it.count }
        try {
            change()
        } finally {
            publish(leftBatch = before - batch.rows.sumOf { it.count })
        }
    }

    /** Stops reading and releases the photos still waiting; call when the batch is added, discarded or abandoned. */
    fun close() {
        job.cancel()
        synchronized(unreleased) { unreleased.toList() }.forEach(::release)
    }

    private suspend fun read(photo: PhotoId) {
        val number = try {
            source.baseNumberIn(photo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        lock.withLock {
            batch.read(photo, number)
            publish(readOne = true)
        }
    }

    /** [leftBatch] is how many cards were just removed or added, so the counts only describe what is still in the batch. */
    private fun publish(readOne: Boolean = false, leftBatch: Int = 0) {
        val rows = batch.rows
        reviewRows.value = rows
        state.update {
            BulkStatus(it.snapped - leftBatch, it.read - leftBatch + if (readOne) 1 else 0, rows.count { row -> row !is BatchRow.Resolved })
        }
    }

    private fun release(photo: PhotoId) {
        if (synchronized(unreleased) { unreleased.remove(photo) }) source.release(photo)
    }
}
