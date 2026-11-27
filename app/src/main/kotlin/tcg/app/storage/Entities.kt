package tcg.app.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.isCustom
/** Where a Printing row came from: the downloaded Catalog, or recorded by the collector. */
enum class PrintingOrigin { Catalog, Custom }

/**
 * The one Printing table: Catalog rows and Custom Printings. Deliberately no foreign key to the Collection tables;
 * an Owned Copy refers to a row by (game, language, sourceId) only. [noLongerInCatalog] rows keep their last-known details.
 */
@Entity(
    tableName = "printing",
    primaryKeys = ["game", "language", "sourceId"],
    indices = [Index("game", "language", "baseNumber")],
)
data class PrintingEntity(
    val game: Game,
    val language: Language,
    val sourceId: String,
    val baseNumber: String,
    val releaseProduct: String,
    val rarity: String,
    val name: String,
    val imageUrl: String,
    val variant: Variant?,
    val productCode: String?,
    /** Tags joined by a newline; null means unknown. */
    val treatments: String?,
    val origin: PrintingOrigin,
    val noLongerInCatalog: Boolean,
)

internal fun Printing.toEntity() = PrintingEntity(
    game = identifier.game,
    language = identifier.language,
    sourceId = identifier.sourceId,
    baseNumber = baseNumber,
    releaseProduct = releaseProduct,
    rarity = rarity,
    name = name,
    imageUrl = imageUrl,
    variant = variant,
    productCode = productCode,
    treatments = treatments?.joinToString("\n"),
    origin = if (identifier.isCustom) PrintingOrigin.Custom else PrintingOrigin.Catalog,
    noLongerInCatalog = noLongerInCatalog,
)

internal fun PrintingEntity.toDomain() = Printing(
    identifier = PrintingIdentifier(game, language, sourceId),
    baseNumber = baseNumber,
    releaseProduct = releaseProduct,
    rarity = rarity,
    name = name,
    imageUrl = imageUrl,
    variant = variant,
    productCode = productCode,
    treatments = treatments?.let { if (it.isEmpty()) emptyList() else it.split("\n") },
    noLongerInCatalog = noLongerInCatalog,
)

/** Which source version each stored Catalog came from, so the next check can skip an unchanged file. */
@Entity(tableName = "catalog_version", primaryKeys = ["game", "language"])
data class CatalogVersionEntity(
    val game: Game,
    val language: Language,
    val version: String?,
    /** The ISO-8601 instant the stored Catalog file was generated. */
    val generatedAt: String? = null,
)

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** The single row holding what the app remembers about backups; see [tcg.domain.backup.BackupStatus]. */
@Entity(tableName = "backup_status")
data class BackupStatusEntity(
    @PrimaryKey val id: Int = 0,
    val lastBackupAtMillis: Long?,
    val backedUpFingerprint: String?,
    val restoredSinceBackup: Boolean,
)

/**
 * Collection table. The Printing is a plain (game, language, sourceId) reference with no foreign key
 * into the Catalog, so a Catalog refresh can never alter or block an Owned Copy.
 */
@Entity(
    tableName = "owned_copy",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("game", "language", "sourceId"), Index("locationId")],
)
data class OwnedCopyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val game: Game,
    val language: Language,
    val sourceId: String,
    val condition: Condition,
    val locationId: Long?,
    val notes: String,
)
