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
    /** Null for an Unresolved Printing, which the Catalog has no record of. */
    val catalogEntry: Printing?,
    val ownedCopyCount: Int,
) {
    val baseNumber: String? get() = catalogEntry?.baseNumber?.takeIf { it.isNotBlank() }
    val name: String? get() = catalogEntry?.name
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
class CollectionList(private val collection: CollectionStore, private val catalog: CatalogStore) {
    suspend fun rows(search: String = "", filter: CollectionFilter = CollectionFilter()): List<CollectionRow> =
        collection.countsByPrinting()
            .mapNotNull { (printing, count) -> row(printing, count, filter) }
            .filter { it.matches(search.trim()) }
            .sortedWith(displayOrder)

    /** The Release Products of every owned Printing the Catalog knows, for choosing a filter. */
    suspend fun releaseProducts(): List<String> =
        collection.countsByPrinting().keys
            .mapNotNull { catalog.find(it)?.releaseProduct?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()

    /** The Locations Owned Copies can be filtered by. */
    suspend fun locations(): List<Location> = collection.locations()

    private suspend fun row(printing: PrintingIdentifier, count: Int, filter: CollectionFilter): CollectionRow? {
        val entry = catalog.find(printing)
        if (filter.language != null && printing.language != filter.language) return null
        if (filter.releaseProduct != null && entry?.releaseProduct != filter.releaseProduct) return null
        if (filter.variant != null && (entry == null || OnePiece.variantOf(printing.sourceId) != filter.variant)) return null
        val copies = if (filter.location == null) count else collection.ownedCopies(printing).count { it.location == filter.location }
        return if (copies == 0) null else CollectionRow(printing, entry, copies)
    }

    private fun CollectionRow.matches(query: String): Boolean = when {
        query.isEmpty() -> true
        catalogEntry == null -> printing.sourceId.contains(query, ignoreCase = true)
        else -> catalogEntry.baseNumber.contains(query, ignoreCase = true) ||
            catalogEntry.name.contains(query, ignoreCase = true)
    }

    private companion object {
        val displayOrder = compareBy<CollectionRow> { it.baseNumber == null }
            .thenBy { it.baseNumber.orEmpty() }
            .thenBy { it.printing.language }
            .thenBy { it.printing.game }
            .thenBy { it.printing.sourceId }
    }
}
