package tcg.domain.catalog

import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.isCustom

/** The refresh rules for one Game and Language, shared by every [tcg.domain.CatalogStore] so they cannot drift apart. */
object CatalogRefresh {
    /**
     * The Catalog rows to store after a refresh. Every Printing the file lists is stored with its new details and no flag.
     * A [stored] Catalog row the file drops stays, flagged, with its last-known details if its identifier is in [inUse]
     * (an Owned Copy uses it), and is deleted otherwise. Custom rows are never touched, so they are ignored here.
     * A re-ID'd card is never remapped: its old row is flagged and the new ID is a separate row.
     */
    fun apply(stored: List<Printing>, incoming: List<Printing>, inUse: Set<PrintingIdentifier>): List<Printing> {
        val listed = incoming.mapTo(mutableSetOf()) { it.identifier }
        val kept = stored
            .filter { !it.identifier.isCustom && it.identifier !in listed && it.identifier in inUse }
            .map { it.copy(noLongerInCatalog = true) }
        return incoming.map { it.copy(noLongerInCatalog = false) } + kept
    }
}
