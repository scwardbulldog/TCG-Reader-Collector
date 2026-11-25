package tcg.app.catalog

import tcg.domain.Language
import tcg.domain.catalog.CatalogTarget
import tcg.domain.catalog.CatalogUpdateResult

fun Map<CatalogTarget, CatalogUpdateResult>.summary(): String {
    val failures = entries.filter { it.value is CatalogUpdateResult.Failed }
    if (failures.isNotEmpty()) {
        return failures.joinToString("; ") { (target, result) ->
            "${target.language.label()} catalog not updated: ${(result as CatalogUpdateResult.Failed).reason}"
        }
    }
    val updated = entries.filter { it.value is CatalogUpdateResult.Updated }
    if (updated.isEmpty()) return "Catalog is up to date"
    return "Catalog updated: " + updated.joinToString(", ") { (target, result) ->
        "${target.language.label()} ${(result as CatalogUpdateResult.Updated).printingCount} printings"
    }
}

private fun Language.label() = when (this) {
    Language.English -> "EN"
    Language.Japanese -> "JP"
}
