package tcg.domain.scanning

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.OwnedCopy
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing

/** What a Base Number (read or typed) turned up in the session's Language. */
sealed interface ScanLookup {
    /**
     * Candidate Printings in display order. [preselected] is set only when there is exactly one
     * match; it still needs the collector's tap before anything is added.
     */
    data class Candidates(val printings: List<Printing>, val preselected: Printing?) : ScanLookup

    /** Nothing in the Catalog matches; the collector can search manually or skip. */
    data class NoMatch(val baseNumber: String) : ScanLookup
}

/** Input for one Scan Session: finds candidate Printings and adds Owned Copies the collector confirms. */
class ScanSessionInput(
    val session: ScanSession,
    private val catalog: CatalogStore,
    private val collection: CollectionStore,
) {
    suspend fun lookup(baseNumber: String): ScanLookup {
        val typed = baseNumber.trim()
        val printings = resolveCandidates(catalog, session.game, session.language, typed.uppercase())
        if (printings.isEmpty()) return ScanLookup.NoMatch(typed)
        return ScanLookup.Candidates(printings, preselected = printings.singleOrNull())
    }

    /** Manual search by Base Number or name, in the session's Language only. */
    suspend fun search(query: String): List<Printing> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return catalog.search(session.game, session.language, trimmed)
            .sortedWith(compareBy({ it.baseNumber }, { candidateRank(it) }, { it.identifier.sourceId }))
    }

    /**
     * Adds [count] separate Owned Copies of [printing], each in the session Location. [condition]
     * overrides the session default for this add only.
     */
    suspend fun add(printing: Printing, count: Int = 1, condition: Condition = session.defaultCondition): List<OwnedCopy> {
        require(count >= 1) { "count must be at least 1" }
        require(printing.identifier.game == session.game && printing.identifier.language == session.language) {
            "${printing.identifier} is outside this Scan Session"
        }
        return List(count) {
            collection.add(NewOwnedCopy(printing.identifier, condition, session.location.id))
        }
    }
}
