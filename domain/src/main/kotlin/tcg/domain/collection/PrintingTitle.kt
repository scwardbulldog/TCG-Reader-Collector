package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.isCustom

/** What a Printing view shows as its heading: the [title] and, when there is one, smaller [subtitle] text. */
data class PrintingTitle(val title: String, val subtitle: String?) {
    companion object {
        /**
         * English is the reading Language. [englishName] is the Card's English name, null when it has none.
         * A Custom Printing keeps its own label as the title; a Japanese Printing keeps its own name as the subtitle;
         * a Printing whose Card has no English name shows its own name alone.
         */
        fun of(printing: Printing, englishName: String?): PrintingTitle = when {
            printing.identifier.isCustom -> PrintingTitle(printing.name, englishName)
            englishName == null -> PrintingTitle(printing.name, null)
            printing.identifier.language == Language.Japanese && printing.name != englishName -> PrintingTitle(englishName, printing.name)
            else -> PrintingTitle(englishName, null)
        }
    }
}

/** Looks up Card names in the Catalog to title Printings. */
class PrintingTitles(private val catalog: CatalogStore) {
    /** The Card's English name, from the Catalog's English Printings of [baseNumber]; null when there is none. */
    suspend fun englishName(game: Game, baseNumber: String): String? {
        if (baseNumber.isBlank()) return null
        return Card(game, baseNumber, catalog.findByBaseNumber(game, Language.English, baseNumber)).name(Language.English)
    }

    suspend fun of(printing: Printing): PrintingTitle =
        PrintingTitle.of(printing, englishName(printing.identifier.game, printing.baseNumber))
}

/** Treatments joined for display; null when unknown or empty, because no Treatment means unknown, never "plain". */
val Printing.treatmentText: String? get() = treatments?.takeIf { it.isNotEmpty() }?.joinToString(", ")

/** The Release Product with its Product code in brackets when it has one; null when neither is known. */
val Printing.productText: String?
    get() {
        val code = productCode?.takeIf { it.isNotBlank() }
        return when {
            releaseProduct.isBlank() -> code
            code == null -> releaseProduct
            else -> "$releaseProduct ($code)"
        }
    }
