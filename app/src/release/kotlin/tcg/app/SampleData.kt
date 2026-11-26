package tcg.app

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore

/** Release builds ship no sample data; the menu entry is never shown. */
object SampleData {
    @Suppress("UNUSED_PARAMETER")
    suspend fun seed(catalog: CatalogStore, collection: CollectionStore) = Unit
}
