package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import java.time.Instant

/** One Collection list row: a Printing and how many Owned Copies of it exist. */
data class CollectionRow(
    val printing: PrintingIdentifier,
    /** The Printing's details, which are the last-known ones when [noLongerInCatalog]; null for an Unresolved Printing. */
    val details: Printing?,
    val ownedCopyCount: Int,
) {
    val baseNumber: String? get() = details?.baseNumber?.takeIf { it.isNotBlank() }
    val name: String? get() = details?.name

    /** A Catalog refresh dropped this Printing, so [details] are the last-known ones. */
    val noLongerInCatalog: Boolean get() = details?.noLongerInCatalog == true

    /** The Catalog has never had this Printing (e.g. after a restore): only its identifier is known. */
    val unresolved: Boolean get() = details == null
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

/** How the Collection list is ordered; ties fall back to the Base Number order. */
enum class CollectionSort {
    BaseNumber,
    Name,

    /** Newest first, by the newest Owned Copy of each Printing (ids only ever grow, so they stand in for the date). */
    DateAdded,

    /** Product code ascending, Printings with none last. For sorting and grouping only. */
    ProductCode,
}

/** A Release Product to filter by, with its Product code when the Catalog has one. */
data class ReleaseProductOption(val releaseProduct: String, val productCode: String?) {
    /** The Product code before the name when present. */
    val label: String get() = if (productCode == null) releaseProduct else "$productCode · $releaseProduct"
}

/** A Location with how many Owned Copies are in it. */
data class LocationUse(val location: Location, val copies: Int)

/** The Collection grouped by Printing, with search, filters and the default display order. */
class CollectionList(private val collection: CollectionStore, private val catalog: CatalogStore) {
    private val printings = PrintingResolver(collection, catalog)
    private val cards = Cards(catalog, collection)

    /** [search] matches a Printing's own details, or the Base Number or any Printing name of its Card in either Language. */
    suspend fun rows(
        search: String = "",
        filter: CollectionFilter = CollectionFilter(),
        sort: CollectionSort = CollectionSort.BaseNumber,
    ): List<CollectionRow> {
        val query = search.trim()
        val matchedCards = if (query.isEmpty()) emptyMap() else Game.entries.associateWith { cards.baseNumbers(it, query) }
        return collection.countsByPrinting()
            .mapNotNull { (printing, count) -> row(printing, count, filter, sort) }
            .filter { it.row.matches(query) || it.row.baseNumber in matchedCards[it.row.printing.game].orEmpty() }
            .sortedWith(order(sort))
            .map { it.row }
    }

    /** The Release Products of every owned Printing whose details are known, for choosing a filter; by Product code, none last. */
    suspend fun releaseProducts(): List<ReleaseProductOption> =
        collection.countsByPrinting().keys
            .mapNotNull { printings.resolve(it) }
            .filter { it.releaseProduct.isNotBlank() }
            .groupBy { it.releaseProduct }
            .map { (product, group) -> ReleaseProductOption(product, group.firstNotNullOfOrNull { it.productCode?.takeIf(String::isNotBlank) }) }
            .sortedWith(compareBy<ReleaseProductOption, String?>(nullsLast()) { it.productCode }.thenBy { it.releaseProduct })

    /** The Locations Owned Copies can be filtered by. */
    suspend fun locations(): List<Location> = collection.locations()

    /** Every Location with the number of Owned Copies in it, for managing them. */
    suspend fun locationUses(): List<LocationUse> =
        collection.locations().map { LocationUse(it, collection.copiesIn(it.id)) }

    suspend fun renameLocation(id: LocationId, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) collection.renameLocation(id, trimmed)
    }

    /** Owned Copies in the Location keep everything else and get Location None. */
    suspend fun deleteLocation(id: LocationId) = collection.deleteLocation(id)

    /** When the oldest stored Catalog file was generated; null if no stored Catalog recorded it. */
    suspend fun catalogGeneratedAt(): Instant? = catalog.catalogGeneratedAt()

    private class Entry(val row: CollectionRow, val newestCopy: Long)

    private suspend fun row(printing: PrintingIdentifier, count: Int, filter: CollectionFilter, sort: CollectionSort): Entry? {
        val details = printings.resolve(printing)
        if (filter.language != null && printing.language != filter.language) return null
        if (filter.releaseProduct != null && details?.releaseProduct != filter.releaseProduct) return null
        if (filter.variant != null && (details == null || details.variant != filter.variant)) return null
        if (filter.location == null && sort != CollectionSort.DateAdded) return Entry(CollectionRow(printing, details, count), 0)
        val copies = collection.ownedCopies(printing).filter { filter.location == null || it.location == filter.location }
        return if (copies.isEmpty()) null else Entry(CollectionRow(printing, details, copies.size), copies.maxOf { it.id.value })
    }

    private fun CollectionRow.matches(query: String): Boolean = when {
        query.isEmpty() -> true
        details == null -> printing.sourceId.contains(query, ignoreCase = true)
        else -> details.baseNumber.contains(query, ignoreCase = true) ||
            details.name.contains(query, ignoreCase = true)
    }

    private fun order(sort: CollectionSort): Comparator<Entry> {
        val byBaseNumber = compareBy<Entry> { it.row.baseNumber == null }
            .thenBy(baseNumberOrder) { it.row.baseNumber.orEmpty() }
            .thenBy { it.row.printing.language }
            .thenBy { it.row.printing.game }
            .thenBy { it.row.printing.sourceId }
        return when (sort) {
            CollectionSort.BaseNumber -> byBaseNumber
            CollectionSort.Name -> compareBy<Entry, String?>(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.row.name }.then(byBaseNumber)
            CollectionSort.DateAdded -> compareByDescending<Entry> { it.newestCopy }.then(byBaseNumber)
            CollectionSort.ProductCode -> compareBy<Entry, String?>(nullsLast()) { it.row.details?.productCode?.takeIf(String::isNotBlank) }.then(byBaseNumber)
        }
    }
}

