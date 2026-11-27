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
        PrintingEntity::class, CatalogVersionEntity::class, LocationEntity::class, OwnedCopyEntity::class,
        BackupStatusEntity::class,
    ],
    version = 6,
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

        /**
         * Merges `known_printing` into the one `printing` table (custom rows stay Custom, the rest are flagged until a
         * v2 refresh lists them) and empties the Catalog so the next launch downloads the v2 files. The Collection tables
         * are untouched. SQLite runs a migration in one transaction, so a failure leaves the version 4 database as it was.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `printing` (`game` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, `baseNumber` TEXT NOT NULL, `releaseProduct` TEXT NOT NULL, " +
                        "`rarity` TEXT NOT NULL, `name` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `variant` TEXT, " +
                        "`productCode` TEXT, `treatments` TEXT, `origin` TEXT NOT NULL, `noLongerInCatalog` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`game`, `language`, `sourceId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_printing_game_language_baseNumber` ON `printing` (`game`, `language`, `baseNumber`)")
                db.execSQL(
                    "INSERT INTO `printing` (`game`, `language`, `sourceId`, `baseNumber`, `releaseProduct`, `rarity`, " +
                        "`name`, `imageUrl`, `variant`, `productCode`, `treatments`, `origin`, `noLongerInCatalog`) " +
                        "SELECT `game`, `language`, `sourceId`, `baseNumber`, `releaseProduct`, `rarity`, `name`, `imageUrl`, " +
                        "NULL, NULL, NULL, " +
                        "CASE WHEN `sourceId` LIKE 'custom:%' THEN 'Custom' ELSE 'Catalog' END, " +
                        "CASE WHEN `sourceId` LIKE 'custom:%' THEN 0 ELSE 1 END FROM `known_printing`",
                )
                db.execSQL("DROP TABLE `known_printing`")
                db.execSQL("DROP TABLE `catalog_printing`")
                db.execSQL("DROP TABLE `catalog_version`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `catalog_version` (`game` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`version` TEXT, PRIMARY KEY(`game`, `language`))",
                )
            }
        }

        /** Adds the Catalog file's generation time to the Catalog version table; it fills in at the next refresh. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `catalog_version` ADD COLUMN `generatedAt` TEXT")
                // Forget the stored versions so the next launch downloads the files and records their dates.
                db.execSQL("UPDATE `catalog_version` SET `version` = NULL")
            }
        }

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
