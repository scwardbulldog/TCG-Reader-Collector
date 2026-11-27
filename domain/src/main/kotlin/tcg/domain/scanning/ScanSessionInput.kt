package tcg.domain.scanning

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.OwnedCopy
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.onepiece.OnePiece
import tcg.domain.collection.Cards
import tcg.domain.collection.CustomPrintings
import tcg.domain.collection.PrintingTitle

/** One row of a Card-first name search: a Card in the session's Language. */
data class CardMatch(
    val baseNumber: String,
    val title: PrintingTitle,
    /** How many Printings the Card has in the session's Language, Custom Printings included. */
    val printingCount: Int,
    /** The Normal Printing's image, else another Printing's; empty when none has one. */
    val imageUrl: String,
)

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

/** What one typed search turned up in the session's current Language. */
sealed interface ScanSearch {
    /** The text was a Base Number with Printings: straight to the proposals. */
    data class Proposals(val candidates: ScanLookup.Candidates) : ScanSearch

    /** The text matched Cards by name or part of a Base Number. */
    data class Cards(val matches: List<CardMatch>) : ScanSearch

    /** Neither a Base Number nor a name matches; the collector can create a Custom Printing or skip. */
    data class NoMatch(val text: String) : ScanSearch
}

/** Input for one Scan Session: finds candidate Printings and adds Owned Copies the collector confirms. */
class ScanSessionInput(
    val session: ScanSession,
    private val catalog: CatalogStore,
    private val collection: CollectionStore,
) {
    private val customPrintings = CustomPrintings(collection)
    private val cards = Cards(catalog, collection)

    suspend fun lookup(baseNumber: String): ScanLookup {
        val typed = baseNumber.trim()
        val base = OnePiece.typedBaseNumber(typed) ?: typed.uppercase()
        val language = session.language // read once: a switch mid-lookup must not mix Languages
        val printings = (
            catalog.findByBaseNumber(session.game, language, base) +
                customPrintings.forBaseNumber(session.game, language, base)
            ).inCandidateOrder()
        if (printings.isEmpty()) return ScanLookup.NoMatch(typed)
        return ScanLookup.Candidates(printings, preselected = printings.singleOrNull())
    }

    /** The one typed search: a Base Number goes straight to its proposals, anything else lists Cards, and nothing found is a no-match. */
    suspend fun find(text: String): ScanSearch {
        (lookup(text) as? ScanLookup.Candidates)?.let { return ScanSearch.Proposals(it) }
        val matches = searchCards(text)
        return if (matches.isEmpty()) ScanSearch.NoMatch(text.trim()) else ScanSearch.Cards(matches)
    }

    /** Creates a Custom Printing in the session's Language for a no-match and proposes it like any candidate. */
    suspend fun createCustomPrinting(baseNumber: String, label: String): Printing =
        customPrintings.create(session.game, session.language, baseNumber, label)

    /** Drops a Custom Printing the collector created but then did not add, so no unused one is left behind. */
    suspend fun discardCustomPrinting(printing: Printing) {
        customPrintings.delete(printing.identifier)
    }

    /**
     * Manual search by name or part of a Base Number, in the session's Language only: one row per Card in
     * natural Base Number order, no ranking. Tap a row's Base Number into [lookup] for its proposals.
     */
    suspend fun searchCards(query: String): List<CardMatch> {
        val language = session.language
        return cards.search(session.game, query, language).map { card ->
            val printings = card.printings(language)
            val english = card.name(Language.English)
            val own = card.name(language)
            CardMatch(
                baseNumber = card.baseNumber,
                title = PrintingTitle(
                    english ?: own.orEmpty(),
                    own.takeIf { english != null && language == Language.Japanese && it != english },
                ),
                printingCount = printings.size,
                imageUrl = printings.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl.orEmpty(),
            )
        }
    }

    /** How many Owned Copies the Collection holds of each of [printings]; absent means none. */
    suspend fun ownedCounts(printings: Collection<Printing>): Map<PrintingIdentifier, Int> {
        val counts = collection.countsByPrinting()
        return printings.map { it.identifier }.filter { it in counts }.associateWith { counts.getValue(it) }
    }

    /** Removes [copies] just added, leaving the Collection as it was before the add. */
    suspend fun undo(copies: List<OwnedCopy>) {
        copies.forEach { collection.delete(it.id) }
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
