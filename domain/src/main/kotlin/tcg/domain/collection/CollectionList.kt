package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.onepiece.OnePiece

/** One Collection list row: a Printing and how many Owned Copies of it exist. */
data class CollectionRow(
    val printing: PrintingIdentifier,
    /** The Catalog's details, or the last-known ones when [noLongerInCatalog]; null for an Unresolved Printing. */
    val details: Printing?,
    val ownedCopyCount: Int,
    /** A Catalog refresh dropped this Printing, so [details] are the last-known ones. */
    val noLongerInCatalog: Boolean = false,
) {
    val baseNumber: String? get() = details?.baseNumber?.takeIf { it.isNotBlank() }
    val name: String? get() = details?.name
}

/** Narrows the Collection list; every set field must match (AND), and unset fields match everything. */
data class CollectionFilter(
    val releaseProduct: String? = null,
    val language: Language? = null,
    val variant: Variant? = null,
    val location: LocationId? = null,
) {
    val isActive: Boolean get() = this != CollectionFilter()
}

/** The Collection grouped by Printing, with search, filters and the default display order. */
class CollectionList(private val collection: CollectionStore, catalog: CatalogStore) {
    private val printings = LastKnownPrintings(collection, catalog)

    suspend fun rows(search: String = "", filter: CollectionFilter = CollectionFilter()): List<CollectionRow> =
        collection.countsByPrinting()
            .mapNotNull { (printing, count) -> row(printing, count, filter) }
            .filter { it.matches(search.trim()) }
            .sortedWith(displayOrder)

    /** The Release Products of every owned Printing whose details are known, for choosing a filter. */
    suspend fun releaseProducts(): List<String> =
        collection.countsByPrinting().keys
            .mapNotNull { printings.resolve(it)?.printing?.releaseProduct?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()

    /** The Locations Owned Copies can be filtered by. */
    suspend fun locations(): List<Location> = collection.locations()

    private suspend fun row(printing: PrintingIdentifier, count: Int, filter: CollectionFilter): CollectionRow? {
        val entry = printings.resolve(printing)
        val details = entry?.printing
        if (filter.language != null && printing.language != filter.language) return null
        if (filter.releaseProduct != null && details?.releaseProduct != filter.releaseProduct) return null
        if (filter.variant != null && (details == null || OnePiece.variantOf(printing.sourceId) != filter.variant)) return null
        val copies = if (filter.location == null) count else collection.ownedCopies(printing).count { it.location == filter.location }
        return if (copies == 0) null else CollectionRow(printing, details, copies, entry?.noLongerInCatalog ?: false)
    }

    private fun CollectionRow.matches(query: String): Boolean = when {
        query.isEmpty() -> true
        details == null -> printing.sourceId.contains(query, ignoreCase = true)
        else -> details.baseNumber.contains(query, ignoreCase = true) ||
            details.name.contains(query, ignoreCase = true)
    }

    private companion object {
        val displayOrder = compareBy<CollectionRow> { it.baseNumber == null }
            .thenBy { it.baseNumber.orEmpty() }
            .thenBy { it.printing.language }
            .thenBy { it.printing.game }
            .thenBy { it.printing.sourceId }
    }
}
