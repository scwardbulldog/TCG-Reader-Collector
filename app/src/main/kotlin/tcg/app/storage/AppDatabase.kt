package tcg.app.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import tcg.domain.CatalogStore
import tcg.domain.CollectionStore

@Database(
    entities = [CatalogPrintingEntity::class, LocationEntity::class, OwnedCopyEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun collectionDao(): CollectionDao

    fun catalogStore(): CatalogStore = RoomCatalogStore(catalogDao())
    fun collectionStore(): CollectionStore = RoomCollectionStore(collectionDao())

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tcg.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
