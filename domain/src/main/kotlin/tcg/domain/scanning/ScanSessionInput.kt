package tcg.domain.scanning

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Location
import tcg.domain.OwnedCopy
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.collection.CustomPrintings

/** What a Base Number (read or typed) turned up in the session's current Language. */
sealed interface ScanLookup {
    /**
     * Candidate Printings in display order. [preselected] is set only when there is exactly one
     * match; it still needs the collector's tap before anything is added.
     */
    data class Candidates(val printings: List<Printing>, val preselected: Printing?) : ScanLookup

    /** Nothing in the Catalog or the Collection's Custom Printings matches; the collector can search manually, create a Custom Printing or skip. */
    data class NoMatch(val baseNumber: String) : ScanLookup
}

/** Input for one Scan Session: finds candidate Printings and adds Owned Copies the collector confirms. */
class ScanSessionInput(
    val session: ScanSession,
    private val catalog: CatalogStore,
    private val collection: CollectionStore,
) {
    private val customPrintings = CustomPrintings(collection)

    suspend fun lookup(baseNumber: String): ScanLookup {
        val typed = baseNumber.trim()
        val base = typed.uppercase()
        val language = session.language // read once: a switch mid-lookup must not mix Languages
        val printings = (
            catalog.findByBaseNumber(session.game, language, base) +
                customPrintings.forBaseNumber(session.game, language, base)
            ).inCandidateOrder()
        if (printings.isEmpty()) return ScanLookup.NoMatch(typed)
        return ScanLookup.Candidates(printings, preselected = printings.singleOrNull())
    }

    /** Creates a Custom Printing in the session's Language for a no-match and proposes it like any candidate. */
    suspend fun createCustomPrinting(baseNumber: String, label: String): Printing =
        customPrintings.create(session.game, session.language, baseNumber, label)

    /** Drops a Custom Printing the collector created but then did not add, so no unused one is left behind. */
    suspend fun discardCustomPrinting(printing: Printing) {
        customPrintings.delete(printing.identifier)
    }

    /** Manual search by Base Number or name, in the session's Language only. */
    suspend fun search(query: String): List<Printing> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val language = session.language
        return (catalog.search(session.game, language, trimmed) + customPrintings.search(session.game, language, trimmed))
            .sortedWith(compareBy({ it.baseNumber }, { candidateRank(it) }, { it.identifier.sourceId }))
    }

    /**
     * Adds [count] separate Owned Copies of [printing], each in [location] (none if null), which then
     * becomes the session's default for the next add. [condition] is Near Mint unless the collector overrides it.
     */
    suspend fun add(
        printing: Printing,
        count: Int = 1,
        condition: Condition = Condition.NearMint,
        location: Location? = session.location,
    ): List<OwnedCopy> {
        require(count >= 1) { "count must be at least 1" }
        require(printing.identifier.game == session.game && printing.identifier.language == session.language) {
            "${printing.identifier} is outside this Scan Session"
        }
        val added = collection.addAll(List(count) { NewOwnedCopy(printing.identifier, condition, location?.id) })
        session.location = location
        return added
    }
}
