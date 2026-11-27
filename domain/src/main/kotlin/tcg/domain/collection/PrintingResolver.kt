package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.isCustom

/**
 * Finds the details of any Printing an Owned Copy can refer to: a Custom Printing, or a Catalog row (flagged
 * [Printing.noLongerInCatalog] when a refresh dropped it and kept its last-known details).
 */
class PrintingResolver(private val collection: CollectionStore, private val catalog: CatalogStore) {
    /** Null for an Unresolved Printing: one the Catalog has no record of. */
    suspend fun resolve(identifier: PrintingIdentifier): Printing? =
        if (identifier.isCustom) collection.customPrinting(identifier) else catalog.find(identifier)
}
