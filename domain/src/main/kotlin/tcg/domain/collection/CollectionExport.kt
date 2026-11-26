package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.OwnedCopy

/** Reads the whole Collection, across every Game and Language, and renders it as CSV. */
class CollectionExport(private val collection: CollectionStore, catalog: CatalogStore) {
    private val printings = LastKnownPrintings(collection, catalog)

    suspend fun csv(): String {
        val owned = collection.countsByPrinting().keys
        val details = owned.mapNotNull { id -> printings.resolve(id)?.let { id to it.printing } }.toMap()
        val copies = owned.flatMap { collection.ownedCopies(it) }
            .sortedWith(
                compareBy<OwnedCopy> { details[it.printing]?.baseNumber.isNullOrBlank() }
                    .thenBy { details[it.printing]?.baseNumber.orEmpty() }
                    .thenBy { it.printing.language }
                    .thenBy { it.printing.game }
                    .thenBy { it.printing.sourceId }
                    .thenBy { it.id.value },
            )
        val locations = collection.locations().associate { it.id to it.name }
        return CollectionCsv.build(copies, details, locations)
    }
}
