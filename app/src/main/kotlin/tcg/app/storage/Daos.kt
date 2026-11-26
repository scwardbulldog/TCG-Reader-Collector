package tcg.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import tcg.domain.Game
import tcg.domain.Language

@Dao
interface CatalogDao {
    @Query("DELETE FROM catalog_printing WHERE game = :game AND language = :language")
    suspend fun deleteAll(game: Game, language: Language)

    @Insert
    suspend fun insertAll(printings: List<CatalogPrintingEntity>)

    @Transaction
    suspend fun replaceAll(game: Game, language: Language, printings: List<CatalogPrintingEntity>, version: String?) {
        deleteAll(game, language)
        insertAll(printings)
        upsertVersion(CatalogVersionEntity(game, language, version))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(version: CatalogVersionEntity)

    @Query("SELECT version FROM catalog_version WHERE game = :game AND language = :language")
    suspend fun version(game: Game, language: Language): String?

    @Query("SELECT * FROM catalog_printing WHERE game = :game AND language = :language AND sourceId = :sourceId")
    suspend fun find(game: Game, language: Language, sourceId: String): CatalogPrintingEntity?

    @Query("SELECT * FROM catalog_printing WHERE game = :game AND language = :language AND baseNumber = :baseNumber")
    suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<CatalogPrintingEntity>

    @Query(
        "SELECT * FROM catalog_printing WHERE game = :game AND language = :language AND " +
            "(baseNumber LIKE '%' || :pattern || '%' ESCAPE '\\' OR name LIKE '%' || :pattern || '%' ESCAPE '\\')",
    )
    suspend fun search(game: Game, language: Language, pattern: String): List<CatalogPrintingEntity>
}

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(copy: OwnedCopyEntity): Long

    /** One transaction: a failed row rolls back the whole insert. */
    @Insert
    suspend fun insertAll(copies: List<OwnedCopyEntity>): List<Long>

    @Query("SELECT * FROM owned_copy WHERE game = :game AND language = :language AND sourceId = :sourceId ORDER BY id")
    suspend fun ownedCopies(game: Game, language: Language, sourceId: String): List<OwnedCopyEntity>

    @Update
    suspend fun update(copy: OwnedCopyEntity)

    @Query("DELETE FROM owned_copy WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT game, language, sourceId, COUNT(*) AS ownedCopyCount FROM owned_copy GROUP BY game, language, sourceId")
    suspend fun countsByPrinting(): List<PrintingCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnownPrintings(printings: List<KnownPrintingEntity>)

    @Query("SELECT * FROM known_printing WHERE game = :game AND language = :language AND sourceId = :sourceId")
    suspend fun knownPrinting(game: Game, language: Language, sourceId: String): KnownPrintingEntity?

    @Insert
    suspend fun insertLocation(location: LocationEntity): Long

    @Query("SELECT * FROM location ORDER BY id")
    suspend fun locations(): List<LocationEntity>

    @Query("UPDATE location SET name = :name WHERE id = :id")
    suspend fun renameLocation(id: Long, name: String)

    @Query("SELECT COUNT(*) FROM owned_copy WHERE locationId = :id")
    suspend fun copiesIn(id: Long): Int

    @Query("DELETE FROM location WHERE id = :id")
    suspend fun deleteLocationRow(id: Long)

    @Transaction
    suspend fun deleteLocationIfEmpty(id: Long): Boolean {
        if (copiesIn(id) > 0) return false
        deleteLocationRow(id)
        return true
    }

    @Query("SELECT * FROM owned_copy ORDER BY id")
    suspend fun allCopies(): List<OwnedCopyEntity>

    @Insert
    suspend fun insertLocations(locations: List<LocationEntity>)

    @Query("DELETE FROM owned_copy")
    suspend fun deleteAllCopies()

    @Query("DELETE FROM location")
    suspend fun deleteAllLocations()

    @Query("DELETE FROM known_printing")
    suspend fun deleteAllKnownPrintings()

    /** One transaction, so the Locations and Owned Copies are read as of the same moment. */
    @Transaction
    suspend fun snapshot(): Pair<List<LocationEntity>, List<OwnedCopyEntity>> = locations() to allCopies()

    /** One transaction: a failed insert rolls back the deletes too, leaving the old Collection in place. */
    @Transaction
    suspend fun replaceAll(locations: List<LocationEntity>, copies: List<OwnedCopyEntity>) {
        deleteAllCopies()
        deleteAllLocations()
        deleteAllKnownPrintings()
        insertLocations(locations)
        insertAll(copies)
    }
}

@Dao
interface BackupStatusDao {
    @Query("SELECT * FROM backup_status WHERE id = 0")
    suspend fun load(): BackupStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(status: BackupStatusEntity)
}

data class PrintingCount(val game: Game, val language: Language, val sourceId: String, val ownedCopyCount: Int)
