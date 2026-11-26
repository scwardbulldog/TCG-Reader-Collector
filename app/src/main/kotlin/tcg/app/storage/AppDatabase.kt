package tcg.app.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.backup.BackupStatusStore

@Database(
    entities = [
        CatalogPrintingEntity::class, CatalogVersionEntity::class, LocationEntity::class, OwnedCopyEntity::class,
        KnownPrintingEntity::class, BackupStatusEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun collectionDao(): CollectionDao
    abstract fun backupStatusDao(): BackupStatusDao

    fun catalogStore(): CatalogStore = RoomCatalogStore(catalogDao())
    fun collectionStore(): CollectionStore = RoomCollectionStore(collectionDao())
    fun backupStatusStore(): BackupStatusStore = RoomBackupStatusStore(backupStatusDao())

    companion object {
        /** Adds the Catalog version table; leaves the Collection and Catalog rows as they are. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_version` (`game` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`version` TEXT, PRIMARY KEY(`game`, `language`))",
                )
            }
        }

        /** Adds the last-known Printing table; leaves the Collection and Catalog rows as they are. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `known_printing` (`game` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, `baseNumber` TEXT NOT NULL, `releaseProduct` TEXT NOT NULL, " +
                        "`rarity` TEXT NOT NULL, `name` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, " +
                        "PRIMARY KEY(`game`, `language`, `sourceId`))",
                )
            }
        }

        /** Adds the backup status table; leaves the Collection and Catalog rows as they are. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `backup_status` (`id` INTEGER NOT NULL, `lastBackupAtMillis` INTEGER, " +
                        "`backedUpFingerprint` TEXT, `restoredSinceBackup` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
