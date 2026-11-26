package tcg.app.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language

/** Catalog table. Deliberately no relation to the Collection tables. */
@Entity(
    tableName = "catalog_printing",
    primaryKeys = ["game", "language", "sourceId"],
    indices = [Index("game", "language", "baseNumber")],
)
data class CatalogPrintingEntity(
    val game: Game,
    val language: Language,
    val sourceId: String,
    val baseNumber: String,
    val releaseProduct: String,
    val rarity: String,
    val name: String,
    val imageUrl: String,
)

/** Which source version each stored Catalog came from, so the next check can skip an unchanged file. */
@Entity(tableName = "catalog_version", primaryKeys = ["game", "language"])
data class CatalogVersionEntity(
    val game: Game,
    val language: Language,
    val version: String?,
)

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * Last-known details of each Printing an Owned Copy refers to, kept with the Collection so they survive a
 * Catalog refresh that drops the Printing. Like [OwnedCopyEntity], no foreign key into the Catalog.
 */
@Entity(tableName = "known_printing", primaryKeys = ["game", "language", "sourceId"])
data class KnownPrintingEntity(
    val game: Game,
    val language: Language,
    val sourceId: String,
    val baseNumber: String,
    val releaseProduct: String,
    val rarity: String,
    val name: String,
    val imageUrl: String,
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
