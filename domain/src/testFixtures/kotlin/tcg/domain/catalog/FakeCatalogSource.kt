package tcg.domain.catalog

import tcg.domain.Game
import tcg.domain.Language

/** Serves canned Catalog files; a target set to [failWith], or with no file, throws like a network error. */
class FakeCatalogSource : CatalogSource {
    private val files = mutableMapOf<CatalogTarget, CatalogFetch.Fetched>()
    private val failures = mutableMapOf<CatalogTarget, Exception>()

    fun publish(game: Game, language: Language, version: String, content: String) {
        files[CatalogTarget(game, language)] = CatalogFetch.Fetched(version, content)
        failures.remove(CatalogTarget(game, language))
    }

    fun failWith(game: Game, language: Language, error: Exception) {
        failures[CatalogTarget(game, language)] = error
    }

    override suspend fun fetch(game: Game, language: Language, knownVersion: String?): CatalogFetch {
        val target = CatalogTarget(game, language)
        failures[target]?.let { throw it }
        val file = files[target] ?: throw java.io.IOException("No Catalog file published for $target")
        return if (file.version == knownVersion) CatalogFetch.UpToDate else file
    }
}
