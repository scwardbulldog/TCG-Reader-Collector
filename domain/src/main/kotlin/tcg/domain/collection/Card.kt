package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.Variant
import tcg.domain.containsFolded
import tcg.domain.isCustom

/**
 * What all Printings sharing a [game] and [baseNumber] have in common. Derived, never stored: it exists wherever at
 * least one Printing does. [printings] are grouped by Language, then Variant (Normal, Reprint, Alternate Art, Custom
 * last), then Product code and Release Product.
 */
data class Card(val game: Game, val baseNumber: String, val printings: List<Printing>) {
    fun printings(language: Language): List<Printing> = printings.filter { it.identifier.language == language }

    /** The Normal Printing's name in [language], else any Catalog Printing's; null when there is none. A Custom label is never a Card name. */
    fun name(language: Language): String? {
        val named = printings(language).filter { !it.identifier.isCustom && it.name.isNotBlank() }
        return (named.firstOrNull { it.variant == Variant.Normal } ?: named.firstOrNull())?.name
    }
}

private fun variantRank(printing: Printing): Int = when (printing.variant) {
    Variant.Normal -> 0
    Variant.Reprint -> 1
    Variant.AlternateArt -> 2
    null -> 3
}

private val printingOrder = compareBy<Printing>({ it.identifier.language }, ::variantRank)
    .thenBy(nullsLast()) { it.productCode }
    .thenBy { it.releaseProduct }
    .thenBy { it.identifier.sourceId }

/** Derives Cards from the Catalog and the Collection's Custom Printings. Flagged (No longer in the Catalog) rows are not offered. */
class Cards(private val catalog: CatalogStore, private val collection: CollectionStore) {
    /** The Card with [baseNumber]; null when no Printing has it. */
    suspend fun of(game: Game, baseNumber: String): Card? =
        build(game, baseNumber, customPrintings(game))

    /**
     * The Cards whose Printing names, in either Language, contain [query] (ignoring case and full-width/half-width
     * forms), by Base Number. [language] keeps only Cards with a Printing in it; each Card still lists all its Printings.
     * Custom labels are not searched: a label is not a Card name.
     */
    suspend fun search(game: Game, query: String, language: Language? = null): List<Card> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val baseNumbers = Language.entries
            .flatMap { catalog.search(game, it, trimmed) }
            .filter { it.name.containsFolded(trimmed) }
            .mapTo(sortedSetOf()) { it.baseNumber }
        val customs = customPrintings(game)
        return baseNumbers.mapNotNull { build(game, it, customs) }
            .filter { language == null || it.printings(language).isNotEmpty() }
    }

    private suspend fun customPrintings(game: Game): List<Printing> =
        Language.entries.flatMap { collection.customPrintings(game, it) }

    private suspend fun build(game: Game, baseNumber: String, customs: List<Printing>): Card? {
        val printings = Language.entries.flatMap { catalog.findByBaseNumber(game, it, baseNumber) } +
            customs.filter { it.baseNumber == baseNumber }
        return if (printings.isEmpty()) null else Card(game, baseNumber, printings.sortedWith(printingOrder))
    }
}
