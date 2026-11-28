package tcg.app.storage

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun migrating_from_3_to_4_keeps_the_collection_and_adds_the_backup_status_table() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'OP01-001', 'NearMint', NULL, 'sleeved')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            assertEquals(1, db.query("SELECT COUNT(*) FROM owned_copy WHERE notes = 'sleeved'").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(0, db.query("SELECT COUNT(*) FROM backup_status").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    @Test
    fun migrating_from_4_to_5_merges_known_printings_into_one_table_and_keeps_the_collection() {
        seedVersion4()

        helper.runMigrationsAndValidate(DB_NAME, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM location WHERE id = 1 AND name = 'Binder 1'"))
            assertEquals(3, count(db, "SELECT COUNT(*) FROM owned_copy"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM owned_copy WHERE sourceId = 'OP01-001' AND condition = 'NearMint' AND locationId = 1 AND notes = 'sleeved'"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM backup_status WHERE lastBackupAtMillis = 123"))
            assertEquals(3, count(db, "SELECT COUNT(*) FROM printing"))
            assertEquals(2, count(db, "SELECT COUNT(*) FROM printing WHERE origin = 'Catalog' AND noLongerInCatalog = 1 AND variant IS NULL"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM printing WHERE origin = 'Custom' AND noLongerInCatalog = 0 AND sourceId = 'custom:1' AND name = 'Milk Box Stamp'"))
            assertEquals(1, count(db, "SELECT COUNT(*) FROM printing WHERE sourceId = 'OP01-001' AND name = 'Zoro (last known)'"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM catalog_version"))
            assertEquals(0, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('known_printing', 'catalog_printing')"))
        }
    }

    @Test
    fun after_migrating_4_to_5_the_first_v2_refresh_unflags_listed_rows_and_keeps_the_rest_flagged() = runTest {
        seedVersion4()
        helper.runMigrationsAndValidate(DB_NAME, 5, true, AppDatabase.MIGRATION_4_5).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()
        try {
            val zoro = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-001")
            val nami = PrintingIdentifier(Game.OnePiece, Language.English, "OP01-016")
            val catalog = db.catalogStore()
            assertNull(catalog.catalogVersion(Game.OnePiece, Language.English))

            catalog.replaceCatalog(
                Game.OnePiece, Language.English,
                listOf(Printing(zoro, "OP01-001", "ROMANCE DAWN [OP-01]", "L", "Roronoa Zoro", "u", Variant.Normal, "OP-01")),
                "v2",
            )

            assertEquals(
                Printing(zoro, "OP01-001", "ROMANCE DAWN [OP-01]", "L", "Roronoa Zoro", "u", Variant.Normal, "OP-01"),
                catalog.find(zoro),
            )
            assertEquals(true, catalog.find(nami)?.noLongerInCatalog)
            assertEquals(3, db.collectionStore().countsByPrinting().values.sum())
            assertEquals("Milk Box Stamp", db.collectionStore().customPrintings(Game.OnePiece, Language.English).single().name)
        } finally {
            db.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    @Test
    fun migrating_from_5_to_6_adds_the_catalog_date_and_forgets_versions_so_the_next_launch_refreshes() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL("INSERT INTO catalog_version VALUES ('OnePiece', 'English', 'etag')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 6, true, AppDatabase.MIGRATION_5_6).use { db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM catalog_version WHERE version IS NULL AND generatedAt IS NULL"))
        }
    }

    @Test
    fun migrating_from_6_to_7_adds_the_date_added_with_none_for_existing_copies() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'OP01-001', 'NearMint', NULL, 'sleeved')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            assertEquals(1, count(db, "SELECT COUNT(*) FROM owned_copy WHERE notes = 'sleeved' AND addedAtMillis IS NULL"))
        }
    }

    private fun seedVersion4() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL("INSERT INTO location (id, name) VALUES (1, 'Binder 1')")
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'OP01-001', 'NearMint', 1, 'sleeved')")
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'OP01-016', 'Damaged', NULL, '')")
            db.execSQL("INSERT INTO owned_copy (game, language, sourceId, condition, locationId, notes) VALUES ('OnePiece', 'English', 'custom:1', 'NearMint', NULL, '')")
            db.execSQL("INSERT INTO known_printing VALUES ('OnePiece', 'English', 'OP01-001', 'OP01-001', 'Romance Dawn', 'L', 'Zoro (last known)', 'u')")
            db.execSQL("INSERT INTO known_printing VALUES ('OnePiece', 'English', 'OP01-016', 'OP01-016', 'Romance Dawn', 'R', 'Nami', 'u')")
            db.execSQL("INSERT INTO known_printing VALUES ('OnePiece', 'English', 'custom:1', 'OP01-001', 'Custom', '', 'Milk Box Stamp', '')")
            db.execSQL("INSERT INTO catalog_printing VALUES ('OnePiece', 'English', 'OP01-001', 'OP01-001', 'Romance Dawn', 'L', 'Zoro', 'u')")
            db.execSQL("INSERT INTO catalog_version VALUES ('OnePiece', 'English', 'v1')")
            db.execSQL("INSERT INTO backup_status VALUES (0, 123, 'abc', 0)")
        }
    }

    private fun count(db: SupportSQLiteDatabase, sql: String) = db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
