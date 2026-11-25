package tcg.app.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore

@Database(
    entities = [CatalogPrintingEntity::class, CatalogVersionEntity::class, LocationEntity::class, OwnedCopyEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun collectionDao(): CollectionDao

    fun catalogStore(): CatalogStore = RoomCatalogStore(catalogDao())
    fun collectionStore(): CollectionStore = RoomCollectionStore(collectionDao())

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

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
