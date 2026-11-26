package tcg.domain.catalog

import kotlin.coroutines.cancellation.CancellationException
import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language

data class CatalogTarget(val game: Game, val language: Language)

sealed interface CatalogUpdateResult {
    data class Updated(val printingCount: Int) : CatalogUpdateResult
    data object UpToDate : CatalogUpdateResult

    /** The previous Catalog is still in place. */
    data class Failed(val reason: String) : CatalogUpdateResult
}

/**
 * Downloads each Catalog file and imports it into the [CatalogStore]. It never touches Owned Copies: the only
 * Collection access is [beforeReplace], which runs just before a target's Catalog is replaced so the Collection
 * can keep the details of Printings the new file may drop. If it fails, the old Catalog stays.
 */
class CatalogUpdater(
    private val source: CatalogSource,
    private val store: CatalogStore,
    private val targets: List<CatalogTarget> = DEFAULT_TARGETS,
    private val beforeReplace: suspend (CatalogTarget) -> Unit = {},
) {
    /** Checks every target. A failure on one leaves its previous Catalog in place and doesn't stop the others. */
    suspend fun update(): Map<CatalogTarget, CatalogUpdateResult> =
        targets.associateWith { target ->
            try {
                updateOne(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CatalogUpdateResult.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
            }
        }

    private suspend fun updateOne(target: CatalogTarget): CatalogUpdateResult {
        val known = store.catalogVersion(target.game, target.language)
        return when (val fetch = source.fetch(target.game, target.language, known)) {
            CatalogFetch.UpToDate -> CatalogUpdateResult.UpToDate
            is CatalogFetch.Fetched -> {
                val printings = CatalogFile.parse(target, fetch.content)
                beforeReplace(target)
                store.replaceCatalog(target.game, target.language, printings, fetch.version)
                CatalogUpdateResult.Updated(printings.size)
            }
        }
    }

    companion object {
        val DEFAULT_TARGETS = listOf(
            CatalogTarget(Game.OnePiece, Language.English),
            CatalogTarget(Game.OnePiece, Language.Japanese),
        )
    }
}
