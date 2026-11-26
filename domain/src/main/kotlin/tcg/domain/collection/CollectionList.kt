package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

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

/** The Collection grouped by Printing, with search and the default display order. */
class CollectionList(private val collection: CollectionStore, private val catalog: CatalogStore) {
    suspend fun rows(search: String = ""): List<CollectionRow> =
        collection.countsByPrinting()
            .map { (printing, count) -> CollectionRow(printing, catalog.find(printing), count) }
            .filter { it.matches(search.trim()) }
            .sortedWith(displayOrder)

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
