package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.CatalogTarget
import tcg.domain.isCustom

/** A Printing's details as the Collection shows them. */
data class PrintingEntry(
    val printing: Printing,
    /** True when the details are the last-known ones because a Catalog refresh dropped the Printing. */
    val noLongerInCatalog: Boolean,
)

/**
 * Keeps the details of every Printing an Owned Copy refers to apart from the Catalog, so a refresh that
 * drops a Printing leaves its Owned Copies with something to show. It never changes or removes an Owned Copy.
 */
class LastKnownPrintings(private val collection: CollectionStore, private val catalog: CatalogStore) {
    /** Saves the Catalog's current details for the owned Printings of [target]. Call it before the Catalog is replaced. */
    suspend fun remember(target: CatalogTarget) {
        val current = collection.countsByPrinting().keys
            .filter { it.game == target.game && it.language == target.language }
            .mapNotNull { catalog.find(it) }
        collection.rememberPrintings(current)
    }

    /** The Catalog's details, else the saved ones; null for an Unresolved Printing. A Custom Printing is never "no longer in the Catalog". */
    suspend fun resolve(identifier: PrintingIdentifier): PrintingEntry? {
        catalog.find(identifier)?.let { return PrintingEntry(it, noLongerInCatalog = false) }
        return collection.lastKnownPrinting(identifier)?.let { PrintingEntry(it, noLongerInCatalog = !identifier.isCustom) }
    }
}
