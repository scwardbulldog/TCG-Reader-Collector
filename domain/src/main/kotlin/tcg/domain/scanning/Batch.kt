package tcg.domain.scanning

import tcg.domain.OwnedCopy
import tcg.domain.Printing

@JvmInline
value class PhotoId(val value: Long)

@JvmInline
value class BatchRowId(val value: Long)

/** One line of the Bulk Add review list. */
sealed interface BatchRow {
    val id: BatchRowId
    val count: Int

    /** Settled on one Printing; reads of the same Printing are summed into [count]. */
    data class Resolved(override val id: BatchRowId, val printing: Printing, override val count: Int) : BatchRow

    /** The Base Number has several Printings, so the collector must pick one; [preselected] is the Normal one. */
    data class Ambiguous(
        override val id: BatchRowId,
        val candidates: List<Printing>,
        override val count: Int,
    ) : BatchRow {
        val preselected: Printing get() = candidates.first()
    }

    /** The photo gave no Base Number ([baseNumber] is null) or the Catalog has no match for it. Always one photo. */
    data class Failed(override val id: BatchRowId, val photo: PhotoId, val baseNumber: String?) : BatchRow {
        override val count: Int get() = 1
    }
}

/**
 * One run of Bulk Add: the photos snapped so far, read into review rows. Nothing reaches the
 * Collection until [addAll]. Not thread-safe; callers serialise reads.
 */
class Batch(private val input: ScanSessionInput) {
    private val entries = mutableListOf<BatchRow>()
    private var nextId = 1L

    /** Failed rows first, in snap order, then the rest in the order they first appeared. */
    val rows: List<BatchRow> get() = entries.filterIsInstance<BatchRow.Failed>() + entries.filter { it !is BatchRow.Failed }

    /** Takes in one photo's read; [baseNumber] is null when the photo could not be read. */
    suspend fun read(photo: PhotoId, baseNumber: String?) {
        when (val lookup = input.lookup(baseNumber.orEmpty())) {
            is ScanLookup.NoMatch -> entries += BatchRow.Failed(newId(), photo, baseNumber?.trim()?.takeIf { it.isNotEmpty() })
            is ScanLookup.Candidates -> place(lookup)
        }
    }

    /** Resolves the ambiguous row [id] to [printing], one of its candidates, merging with a row already on that Printing. */
    fun pick(id: BatchRowId, printing: Printing) {
        val row = entries.filterIsInstance<BatchRow.Ambiguous>().firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("No ambiguous row $id")
        require(row.candidates.any { it.identifier == printing.identifier }) { "${printing.identifier} is not a candidate of row $id" }
        entries.remove(row)
        addResolved(printing, row.count)
    }

    /** Re-reads the failed row [id] from a Base Number the collector typed; it stays failed if the Catalog has no match. */
    suspend fun enterBaseNumber(id: BatchRowId, baseNumber: String) {
        val at = entries.indexOfFirst { it is BatchRow.Failed && it.id == id }
        require(at >= 0) { "No failed row $id" }
        val row = entries[at] as BatchRow.Failed
        when (val lookup = input.lookup(baseNumber)) {
            is ScanLookup.NoMatch -> entries[at] = row.copy(baseNumber = baseNumber.trim().takeIf { it.isNotEmpty() })
            is ScanLookup.Candidates -> {
                entries.removeAt(at)
                place(lookup)
            }
        }
    }

    fun remove(id: BatchRowId) {
        require(entries.removeAll { it.id == id }) { "No row $id" }
    }

    /**
     * Adds every resolved row through [ScanSessionInput.add] and drops it from the batch; failed and
     * ambiguous rows stay for the collector. A row that fails to add stays too, so a retry never double-adds.
     */
    suspend fun addAll(): List<OwnedCopy> {
        val added = mutableListOf<OwnedCopy>()
        for (row in entries.filterIsInstance<BatchRow.Resolved>()) {
            added += input.add(row.printing, row.count)
            entries.remove(row)
        }
        return added
    }

    private fun place(lookup: ScanLookup.Candidates) {
        val printing = lookup.preselected
        if (printing != null) addResolved(printing, 1) else addAmbiguous(lookup.printings, 1)
    }

    private fun addResolved(printing: Printing, count: Int) {
        val at = entries.indexOfFirst { it is BatchRow.Resolved && it.printing.identifier == printing.identifier }
        if (at < 0) {
            entries += BatchRow.Resolved(newId(), printing, count)
        } else {
            val row = entries[at] as BatchRow.Resolved
            entries[at] = row.copy(count = row.count + count)
        }
    }

    private fun addAmbiguous(candidates: List<Printing>, count: Int) {
        val baseNumber = candidates.first().baseNumber
        val at = entries.indexOfFirst { it is BatchRow.Ambiguous && it.candidates.first().baseNumber == baseNumber }
        if (at < 0) {
            entries += BatchRow.Ambiguous(newId(), candidates, count)
        } else {
            val row = entries[at] as BatchRow.Ambiguous
            entries[at] = row.copy(count = row.count + count)
        }
    }

    private fun newId() = BatchRowId(nextId++)
}
