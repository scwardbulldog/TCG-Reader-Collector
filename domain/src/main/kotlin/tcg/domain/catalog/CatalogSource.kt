package tcg.domain.catalog

import tcg.domain.Game
import tcg.domain.Language

/** Where Catalog files come from. The app's implementation uses the network; tests use fixtures. */
interface CatalogSource {
    /**
     * Fetches the published Catalog file for one Game and Language. [knownVersion] is the version
     * already stored on the phone, so the source can answer [CatalogFetch.UpToDate] without a download.
     */
    suspend fun fetch(game: Game, language: Language, knownVersion: String?): CatalogFetch
}

sealed interface CatalogFetch {
    data object UpToDate : CatalogFetch

    /** [content] is the raw Catalog file (see docs/catalog-format.md); [version] identifies it. */
    data class Fetched(val version: String, val content: String) : CatalogFetch
}
