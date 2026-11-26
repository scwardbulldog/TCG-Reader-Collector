package tcg.app.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrating_from_1_to_2_keeps_the_collection_and_catalog_rows() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO location (id, name) VALUES (1, 'Binder 1')")
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'OP01-001', 'NearMint', 1, 'sleeved')")
            db.execSQL("INSERT INTO catalog_printing (game, language, sourceId, baseNumber, releaseProduct, rarity, name, imageUrl) VALUES ('OnePiece', 'English', 'OP01-001', 'OP01-001', 'Romance Dawn', 'L', 'Zoro', 'u')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            assertEquals(1, db.query("SELECT COUNT(*) FROM owned_copy WHERE notes = 'sleeved'").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM catalog_printing").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM catalog_version").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    @Test
    fun migrating_from_2_to_3_keeps_the_collection_and_adds_the_known_printing_table() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'OP01-001', 'NearMint', NULL, 'sleeved')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            assertEquals(1, db.query("SELECT COUNT(*) FROM owned_copy WHERE notes = 'sleeved'").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM known_printing").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
