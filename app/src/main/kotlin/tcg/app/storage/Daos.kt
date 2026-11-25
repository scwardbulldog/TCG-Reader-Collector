package tcg.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import tcg.domain.Game
import tcg.domain.Language

@Dao
interface CatalogDao {
    @Query("DELETE FROM catalog_printing WHERE game = :game AND language = :language")
    suspend fun deleteAll(game: Game, language: Language)

    @Insert
    suspend fun insertAll(printings: List<CatalogPrintingEntity>)

    @Transaction
    suspend fun replaceAll(game: Game, language: Language, printings: List<CatalogPrintingEntity>) {
        deleteAll(game, language)
        insertAll(printings)
    }

    @Query("SELECT * FROM catalog_printing WHERE game = :game AND language = :language AND sourceId = :sourceId")
    suspend fun find(game: Game, language: Language, sourceId: String): CatalogPrintingEntity?

    @Query("SELECT * FROM catalog_printing WHERE game = :game AND language = :language AND baseNumber = :baseNumber")
    suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<CatalogPrintingEntity>
}

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(copy: OwnedCopyEntity): Long

    @Query("SELECT * FROM owned_copy WHERE game = :game AND language = :language AND sourceId = :sourceId ORDER BY id")
    suspend fun ownedCopies(game: Game, language: Language, sourceId: String): List<OwnedCopyEntity>

    @Query("SELECT game, language, sourceId, COUNT(*) AS ownedCopyCount FROM owned_copy GROUP BY game, language, sourceId")
    suspend fun countsByPrinting(): List<PrintingCount>

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
}

data class PrintingCount(val game: Game, val language: Language, val sourceId: String, val ownedCopyCount: Int)
