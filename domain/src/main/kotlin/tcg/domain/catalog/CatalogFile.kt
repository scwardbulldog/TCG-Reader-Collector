package tcg.domain.catalog

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

class CatalogFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads a published Catalog file (docs/catalog-format.md) into Printings, rejecting anything unexpected. */
internal object CatalogFile {
    private const val SUPPORTED_SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class File(
        val schemaVersion: Int,
        val game: String,
        val language: String,
        val printings: List<Entry>,
    )

    @Serializable
    private class Entry(
        val id: String,
        val baseNumber: String,
        val releaseProduct: String,
        val rarity: String,
        val name: String,
        val imageUrl: String,
    )

    fun parse(target: CatalogTarget, content: String): List<Printing> {
        val file = try {
            json.decodeFromString<File>(content)
        } catch (e: Exception) {
            throw CatalogFormatException("Catalog file is not readable: ${e.message}", e)
        }
        if (file.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw CatalogFormatException("Unsupported Catalog schema version ${file.schemaVersion}")
        }
        if (file.game != target.game.fileCode() || file.language != target.language.fileCode()) {
            throw CatalogFormatException("Catalog file is for ${file.game}/${file.language}, expected $target")
        }
        if (file.printings.isEmpty()) throw CatalogFormatException("Catalog file has no Printings")
        if (file.printings.map { it.id }.toSet().size != file.printings.size) {
            throw CatalogFormatException("Catalog file has duplicate Printing ids")
        }
        return file.printings.map {
            Printing(
                identifier = PrintingIdentifier(target.game, target.language, it.id),
                baseNumber = it.baseNumber,
                releaseProduct = it.releaseProduct,
                rarity = it.rarity,
                name = it.name,
                imageUrl = it.imageUrl,
            )
        }
    }

    private fun Game.fileCode() = when (this) {
        Game.OnePiece -> "onepiece"
    }

    private fun Language.fileCode() = when (this) {
        Language.English -> "en"
        Language.Japanese -> "jp"
    }
}
