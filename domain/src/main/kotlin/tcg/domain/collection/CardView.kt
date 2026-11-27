package tcg.domain.collection

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.Variant

/** One Printing on the Card view and how many Owned Copies of it the collector has; 0 shows as nothing. */
data class CardViewEntry(val printing: Printing, val owned: Int)

/** The Printings of one [variant] ([variant] is null for Custom Printings), in proposal order. */
data class CardViewVariant(val variant: Variant?, val entries: List<CardViewEntry>)

data class CardViewLanguage(val language: Language, val variants: List<CardViewVariant>)

/** A read-only Card view. [name] is the English Card name, else the Japanese one; null when only Custom Printings exist. */
data class CardViewState(val baseNumber: String, val name: String?, val languages: List<CardViewLanguage>)

/** Every Printing of one Card in both Languages with the collector's owned counts. Nothing is added from here. */
class CardView(catalog: CatalogStore, private val collection: CollectionStore) {
    private val cards = Cards(catalog, collection)

    /** Null when no Printing has [baseNumber]. */
    suspend fun of(game: Game, baseNumber: String): CardViewState? {
        val card = cards.of(game, baseNumber) ?: return null
        val counts = collection.countsByPrinting()
        val languages = Language.entries.mapNotNull { language ->
            val printings = card.printings(language)
            if (printings.isEmpty()) return@mapNotNull null
            // Printings are already in proposal order, so each Variant's group is contiguous.
            CardViewLanguage(
                language,
                printings.groupBy { it.variant }.map { (variant, group) ->
                    CardViewVariant(variant, group.map { CardViewEntry(it, counts[it.identifier] ?: 0) })
                },
            )
        }
        return CardViewState(baseNumber, card.name(Language.English) ?: card.name(Language.Japanese), languages)
    }
}
