package tcg.domain.collection

import kotlinx.coroutines.CancellationException
import tcg.domain.CUSTOM_ID_PREFIX
import tcg.domain.CardPhotoStore
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
    private val photos: CardPhotoStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * Creates a Custom Printing named [label] for [baseNumber]; the Base Number is kept in upper case, as lookups are.
     * With a [photo] (a capture the [CardPhotoStore] understands) it becomes the Card photo. A photo that cannot be
     * kept does not stop the Printing being created: it just has none, which the caller sees as a null [Printing.cardPhoto].
     */
    suspend fun create(game: Game, language: Language, baseNumber: String, label: String, photo: String? = null): Printing {
        val base = baseNumber.trim().uppercase()
        val name = label.trim()
        require(base.isNotEmpty()) { "A Custom Printing needs a Base Number" }
        require(name.isNotEmpty()) { "A Custom Printing needs a label" }
        val identifier = PrintingIdentifier(game, language, CUSTOM_ID_PREFIX + newId())
        val reference = photo?.let {
            try {
                photos.save(identifier, it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val printing = Printing(
            identifier = identifier,
            baseNumber = base,
            releaseProduct = RELEASE_PRODUCT,
            rarity = "",
            name = name,
            imageUrl = "",
            variant = null,
            cardPhoto = reference,
        )
        try {
            collection.saveCustomPrinting(printing)
        } catch (e: Exception) {
            if (reference != null) photos.delete(identifier)
            throw e
        }
        return printing
    }

    /** Changes the label, shared by every Owned Copy of the Printing. Null if there is no such Custom Printing. */
    suspend fun rename(identifier: PrintingIdentifier, label: String): Printing? {
        val name = label.trim()
        require(name.isNotEmpty()) { "A Custom Printing needs a label" }
        val existing = find(identifier) ?: return null
        return existing.copy(name = name).also { collection.saveCustomPrinting(it) }
    }

    /**
     * Gives the Custom Printing the Card photo from [captured], replacing its photo if it has one. Null if there is
     * no such Custom Printing (a Catalog Printing never has a photo). Throws if the photo cannot be kept, leaving the old one.
     */
    suspend fun setPhoto(identifier: PrintingIdentifier, captured: String): Printing? =
        find(identifier)?.let { attach(it, captured) }

    /** Removes the Card photo, so the Printing falls back to its `imageUrl`. Null if there is no such Custom Printing. */
    suspend fun removePhoto(identifier: PrintingIdentifier): Printing? {
        val existing = find(identifier) ?: return null
        // The reference goes first, so a failure never leaves a Printing pointing at a missing file.
        return existing.copy(cardPhoto = null).also {
            collection.saveCustomPrinting(it)
            photos.delete(identifier)
        }
    }

    /** Deletes the Custom Printing and its photo unless an Owned Copy still uses it. */
    suspend fun delete(identifier: PrintingIdentifier): DeleteCustomPrintingResult {
        if (find(identifier) == null) return DeleteCustomPrintingResult.NotFound
        if (collection.ownedCopies(identifier).isNotEmpty()) return DeleteCustomPrintingResult.InUse
        collection.deleteCustomPrinting(identifier)
        photos.delete(identifier)
        return DeleteCustomPrintingResult.Deleted
    }

    /** The Custom Printings of one Base Number in one Language. */
    suspend fun forBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing> =
        collection.customPrintings(game, language).filter { it.baseNumber == baseNumber }

    private suspend fun attach(printing: Printing, captured: String): Printing {
        val reference = photos.save(printing.identifier, captured)
        return printing.copy(cardPhoto = reference).also { collection.saveCustomPrinting(it) }
    }

    private suspend fun find(identifier: PrintingIdentifier): Printing? =
        if (identifier.isCustom) collection.customPrinting(identifier) else null

    companion object {
        const val RELEASE_PRODUCT = "Custom"
    }
}
