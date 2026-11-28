package tcg.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import tcg.domain.CollectionSnapshot
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Location
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.OwnedCopyId
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.isCustom
import tcg.domain.collection.CustomPrintings
import java.security.MessageDigest
import java.time.Instant

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A backup's JSON read and validated in full. The [snapshot]'s Custom Printings have no Card photo reference yet:
 * [photoNames] says which of them list a photo, and under what name in the zip (empty for a version 1 file).
 */
class ParsedBackup(
    val snapshot: CollectionSnapshot,
    val createdAt: Instant,
    val version: Int,
    val photoNames: Map<PrintingIdentifier, String>,
)

/**
 * The versioned collection JSON of a backup. It holds Owned Copies, Locations and Custom Printings, plus last-known details only for
 * Printings flagged No longer in the Catalog (an optional field, so version 1 files still parse): never other Catalog details or images.
 * Version 2 adds the optional photo name of a Custom Printing, the path of its JPEG in the backup zip; version 1 is the old plain `.json` backup.
 * [parse] checks the whole file and either returns all of it or throws [BackupFormatException].
 */
object BackupFormat {
    private const val FORMAT = "tcg-collection-backup"
    private const val SCHEMA_VERSION = 2
    const val PHOTO_FOLDER = "photos/"

    /** The name of a Custom Printing's photo file, in a backup zip (under [PHOTO_FOLDER]) and in the research export. */
    fun photoFileName(identifier: PrintingIdentifier): String =
        "${identifier.game.code()}_${identifier.language.code()}_${identifier.sourceId.removePrefix("custom:")}.jpg"
            .replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private val json = Json { prettyPrint = true }

    @Serializable
    private class File(
        val format: String,
        val schemaVersion: Int,
        val createdAt: String,
        val locations: List<LocationEntry>,
        val ownedCopies: List<CopyEntry>,
        val customPrintings: List<CustomPrintingEntry> = emptyList(),
        val lastKnownPrintings: List<LastKnownPrintingEntry> = emptyList(),
    )

    @Serializable
    private class LastKnownPrintingEntry(
        val game: String,
        val language: String,
        val sourceId: String,
        val baseNumber: String,
        val releaseProduct: String,
        val rarity: String,
        val name: String,
        val imageUrl: String,
        val variant: String? = null,
        val productCode: String? = null,
        val treatments: List<String>? = null,
    )

    @Serializable
    private class CustomPrintingEntry(
        val game: String,
        val language: String,
        val sourceId: String,
        val baseNumber: String,
        val label: String,
        val photo: String? = null,
    )

    @Serializable
    private class LocationEntry(val id: Long, val name: String)

    @Serializable
    private class CopyEntry(
        val id: Long,
        val game: String,
        val language: String,
        val sourceId: String,
        val condition: String,
        val locationId: Long?,
        val notes: String,
        val addedAt: String? = null,
    )

    /** The collection JSON; each Custom Printing with a Card photo lists it under [photoFileName]. */
    fun serialize(snapshot: CollectionSnapshot, createdAt: Instant): String =
        json.encodeToString(file(snapshot, createdAt.toString()) { PHOTO_FOLDER + photoFileName(it.identifier) })

    /**
     * Identifies the content of a Collection and its Card photos, ignoring when it was written. Equal Collections
     * have equal fingerprints. A photo counts by its reference, which [tcg.domain.CardPhotoStore] changes on every save, so
     * adding, replacing or removing one changes the fingerprint without reading any picture.
     */
    fun fingerprint(snapshot: CollectionSnapshot): String {
        val bytes = json.encodeToString(file(snapshot, createdAt = "") { it.cardPhoto }).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun parse(content: String): ParsedBackup {
        val root = try {
            json.parseToJsonElement(content)
        } catch (e: Exception) {
            throw BackupFormatException("The file is not a Collection backup", e)
        }
        if (root !is JsonObject) throw BackupFormatException("The file is not a Collection backup")
        if ((root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content != FORMAT) {
            throw BackupFormatException("The file is not a Collection backup")
        }
        val version = try {
            root["schemaVersion"]?.jsonPrimitive?.int
        } catch (e: Exception) {
            null
        } ?: throw BackupFormatException("The backup has no readable version")
        if (version != 1 && version != SCHEMA_VERSION) throw BackupFormatException("Unsupported backup version $version")

        val file = try {
            json.decodeFromJsonElement(File.serializer(), root)
        } catch (e: Exception) {
            throw BackupFormatException("The backup file is damaged or incomplete", e)
        }
        val snapshot = snapshot(file)
        val photoNames = photoNames(file)
        if (version == 1 && photoNames.isNotEmpty()) throw BackupFormatException("The backup file is damaged or incomplete")
        return ParsedBackup(snapshot, createdAt(file.createdAt), version, photoNames)
    }

    private fun photoNames(file: File): Map<PrintingIdentifier, String> {
        val names = file.customPrintings.mapNotNull { entry ->
            entry.photo?.let { PrintingIdentifier(game(entry.game), language(entry.language), entry.sourceId) to it }
        }
        if (names.any { !it.second.startsWith(PHOTO_FOLDER) || it.second.length == PHOTO_FOLDER.length }) {
            throw BackupFormatException("The backup lists a Card photo with an invalid name")
        }
        if (names.map { it.second }.toSet().size != names.size) throw BackupFormatException("The backup lists the same Card photo twice")
        return names.toMap()
    }

    private fun file(snapshot: CollectionSnapshot, createdAt: String, photo: (Printing) -> String?) = File(
        format = FORMAT,
        schemaVersion = SCHEMA_VERSION,
        createdAt = createdAt,
        locations = snapshot.locations.sortedBy { it.id.value }.map { LocationEntry(it.id.value, it.name) },
        ownedCopies = snapshot.copies.sortedBy { it.id.value }.map {
            CopyEntry(
                id = it.id.value,
                game = it.printing.game.code(),
                language = it.printing.language.code(),
                sourceId = it.printing.sourceId,
                condition = it.condition.code(),
                locationId = it.location?.value,
                notes = it.notes,
                addedAt = it.addedAt?.toString(),
            )
        },
        customPrintings = snapshot.customPrintings.sortedBy { it.identifier.sourceId }.map {
            CustomPrintingEntry(
                game = it.identifier.game.code(),
                language = it.identifier.language.code(),
                sourceId = it.identifier.sourceId,
                baseNumber = it.baseNumber,
                label = it.name,
                photo = if (it.cardPhoto != null) photo(it) else null,
            )
        },
        lastKnownPrintings = snapshot.lastKnownPrintings
            .sortedWith(compareBy({ it.identifier.game.code() }, { it.identifier.language.code() }, { it.identifier.sourceId }))
            .map {
                LastKnownPrintingEntry(
                    game = it.identifier.game.code(),
                    language = it.identifier.language.code(),
                    sourceId = it.identifier.sourceId,
                    baseNumber = it.baseNumber,
                    releaseProduct = it.releaseProduct,
                    rarity = it.rarity,
                    name = it.name,
                    imageUrl = it.imageUrl,
                    variant = it.variant?.code(),
                    productCode = it.productCode,
                    treatments = it.treatments,
                )
            },
    )

    private fun snapshot(file: File): CollectionSnapshot {
        val locationIds = file.locations.map { it.id }
        if (locationIds.any { it <= 0 }) throw BackupFormatException("The backup has a Location with an invalid id")
        if (locationIds.toSet().size != locationIds.size) throw BackupFormatException("The backup has duplicate Location ids")
        val copyIds = file.ownedCopies.map { it.id }
        if (copyIds.any { it <= 0 }) throw BackupFormatException("The backup has an Owned Copy with an invalid id")
        if (copyIds.toSet().size != copyIds.size) throw BackupFormatException("The backup has duplicate Owned Copy ids")

        val copies = file.ownedCopies.map { entry ->
            if (entry.sourceId.isBlank()) throw BackupFormatException("Owned Copy ${entry.id} has no Printing identifier")
            if (entry.locationId != null && entry.locationId !in locationIds) {
                throw BackupFormatException("Owned Copy ${entry.id} is in a Location the backup does not contain")
            }
            OwnedCopy(
                id = OwnedCopyId(entry.id),
                printing = PrintingIdentifier(game(entry.game), language(entry.language), entry.sourceId),
                condition = condition(entry.condition),
                location = entry.locationId?.let(::LocationId),
                notes = entry.notes,
                addedAt = entry.addedAt?.let {
                    try {
                        Instant.parse(it)
                    } catch (e: Exception) {
                        throw BackupFormatException("Owned Copy ${entry.id} has an invalid date added", e)
                    }
                },
            )
        }
        val customPrintings = file.customPrintings.map { entry ->
            if (!PrintingIdentifier(game(entry.game), language(entry.language), entry.sourceId).isCustom) {
                throw BackupFormatException("The backup has a Custom Printing with an invalid identifier")
            }
            if (entry.baseNumber.isBlank() || entry.label.isBlank()) {
                throw BackupFormatException("The backup has a Custom Printing with no Base Number or label")
            }
            Printing(
                identifier = PrintingIdentifier(game(entry.game), language(entry.language), entry.sourceId),
                baseNumber = entry.baseNumber,
                releaseProduct = CustomPrintings.RELEASE_PRODUCT,
                rarity = "",
                name = entry.label,
                imageUrl = "",
                variant = null,
            )
        }
        if (customPrintings.map { it.identifier }.toSet().size != customPrintings.size) {
            throw BackupFormatException("The backup has duplicate Custom Printings")
        }
        val lastKnown = file.lastKnownPrintings.map { entry ->
            val identifier = PrintingIdentifier(game(entry.game), language(entry.language), entry.sourceId)
            if (entry.sourceId.isBlank() || identifier.isCustom) {
                throw BackupFormatException("The backup has a last-known Printing with an invalid identifier")
            }
            if (entry.baseNumber.isBlank()) throw BackupFormatException("The backup has a last-known Printing with no Base Number")
            Printing(
                identifier = identifier,
                baseNumber = entry.baseNumber,
                releaseProduct = entry.releaseProduct,
                rarity = entry.rarity,
                name = entry.name,
                imageUrl = entry.imageUrl,
                variant = entry.variant?.let(::variant),
                productCode = entry.productCode,
                treatments = entry.treatments,
                noLongerInCatalog = true,
            )
        }
        if (lastKnown.map { it.identifier }.toSet().size != lastKnown.size) {
            throw BackupFormatException("The backup has duplicate last-known Printings")
        }
        return CollectionSnapshot(file.locations.map { Location(LocationId(it.id), it.name) }, copies, customPrintings, lastKnown)
    }

    private fun createdAt(text: String): Instant = try {
        Instant.parse(text)
    } catch (e: Exception) {
        throw BackupFormatException("The backup has an invalid creation time", e)
    }

    private fun Game.code() = when (this) {
        Game.OnePiece -> "onepiece"
    }

    private fun game(code: String) = Game.entries.firstOrNull { it.code() == code }
        ?: throw BackupFormatException("The backup has an unknown Game \"$code\"")

    private fun Language.code() = when (this) {
        Language.English -> "en"
        Language.Japanese -> "jp"
    }

    private fun language(code: String) = Language.entries.firstOrNull { it.code() == code }
        ?: throw BackupFormatException("The backup has an unknown Language \"$code\"")

    private fun Variant.code() = when (this) {
        Variant.Normal -> "normal"
        Variant.AlternateArt -> "alternate-art"
        Variant.Reprint -> "reprint"
    }

    private fun variant(code: String) = Variant.entries.firstOrNull { it.code() == code }
        ?: throw BackupFormatException("The backup has an unknown variant \"$code\"")

    private fun Condition.code() = when (this) {
        Condition.NearMint -> "near_mint"
        Condition.LightlyPlayed -> "lightly_played"
        Condition.ModeratelyPlayed -> "moderately_played"
        Condition.HeavilyPlayed -> "heavily_played"
        Condition.Damaged -> "damaged"
    }

    private fun condition(code: String) = Condition.entries.firstOrNull { it.code() == code }
        ?: throw BackupFormatException("The backup has an unknown condition \"$code\"")
}
