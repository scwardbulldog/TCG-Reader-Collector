package tcg.domain.collection

import tcg.domain.CUSTOM_ID_PREFIX
import tcg.domain.CollectionStore
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.isCustom
import java.util.UUID

enum class DeleteCustomPrintingResult { Deleted, InUse, NotFound }

/**
 * Custom Printings: Printings the collector records because the Catalog lacks the card. They are stored with
 * the Collection, so a Catalog refresh never drops them, and their ids cannot collide with the Catalog's.
 */
class CustomPrintings(
    private val collection: CollectionStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Creates a Custom Printing named [label] for [baseNumber]; the Base Number is kept in upper case, as lookups are. */
    suspend fun create(game: Game, language: Language, baseNumber: String, label: String): Printing {
        val base = baseNumber.trim().uppercase()
        val name = label.trim()
        require(base.isNotEmpty()) { "A Custom Printing needs a Base Number" }
        require(name.isNotEmpty()) { "A Custom Printing needs a label" }
        val printing = Printing(
            identifier = PrintingIdentifier(game, language, CUSTOM_ID_PREFIX + newId()),
            baseNumber = base,
            releaseProduct = RELEASE_PRODUCT,
            rarity = "",
            name = name,
            imageUrl = "",
            variant = null,
        )
        collection.rememberPrintings(listOf(printing))
        return printing
    }

    /** Changes the label, shared by every Owned Copy of the Printing. Null if there is no such Custom Printing. */
    suspend fun rename(identifier: PrintingIdentifier, label: String): Printing? {
        val name = label.trim()
        require(name.isNotEmpty()) { "A Custom Printing needs a label" }
        val existing = find(identifier) ?: return null
        return existing.copy(name = name).also { collection.rememberPrintings(listOf(it)) }
    }

    /** Deletes the Custom Printing unless an Owned Copy still uses it. */
    suspend fun delete(identifier: PrintingIdentifier): DeleteCustomPrintingResult {
        if (find(identifier) == null) return DeleteCustomPrintingResult.NotFound
        if (collection.ownedCopies(identifier).isNotEmpty()) return DeleteCustomPrintingResult.InUse
        collection.deleteCustomPrinting(identifier)
        return DeleteCustomPrintingResult.Deleted
    }

    /** The Custom Printings of one Base Number in one Language. */
    suspend fun forBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing> =
        collection.customPrintings(game, language).filter { it.baseNumber == baseNumber }

    /** The Custom Printings of one Language whose Base Number or label contains [query], ignoring case. */
    suspend fun search(game: Game, language: Language, query: String): List<Printing> =
        collection.customPrintings(game, language).filter {
            it.baseNumber.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        }

    private suspend fun find(identifier: PrintingIdentifier): Printing? =
        if (identifier.isCustom) collection.lastKnownPrinting(identifier) else null

    companion object {
        const val RELEASE_PRODUCT = "Custom"
    }
}
