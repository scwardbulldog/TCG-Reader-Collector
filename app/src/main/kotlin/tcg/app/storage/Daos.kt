package tcg.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.catalog.CatalogRefresh
@Dao
interface CatalogDao {
    @Query("SELECT * FROM printing WHERE game = :game AND language = :language AND origin = 'Catalog'")
    suspend fun catalogRows(game: Game, language: Language): List<PrintingEntity>

    @Query("SELECT DISTINCT sourceId FROM owned_copy WHERE game = :game AND language = :language")
    suspend fun ownedSourceIds(game: Game, language: Language): List<String>

    @Query("DELETE FROM printing WHERE game = :game AND language = :language AND origin = 'Catalog'")
    suspend fun deleteCatalogRows(game: Game, language: Language)

    @Insert
    suspend fun insertAll(printings: List<PrintingEntity>)

    /** One transaction: the stored rows, the Owned Copies that use them and the write all see the same moment. */
    @Transaction
    suspend fun replaceAll(game: Game, language: Language, printings: List<Printing>, version: String?) {
        val stored = catalogRows(game, language).map { it.toDomain() }
        val inUse = ownedSourceIds(game, language).mapTo(mutableSetOf()) { PrintingIdentifier(game, language, it) }
        deleteCatalogRows(game, language)
        insertAll(CatalogRefresh.apply(stored, printings, inUse).map { it.toEntity() })
        upsertVersion(CatalogVersionEntity(game, language, version))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersion(version: CatalogVersionEntity)

    @Query("SELECT version FROM catalog_version WHERE game = :game AND language = :language")
    suspend fun version(game: Game, language: Language): String?

    @Query("SELECT * FROM printing WHERE game = :game AND language = :language AND sourceId = :sourceId AND origin = 'Catalog'")
    suspend fun find(game: Game, language: Language, sourceId: String): PrintingEntity?

    @Query(
        "SELECT * FROM printing WHERE game = :game AND language = :language AND baseNumber = :baseNumber " +
            "AND origin = 'Catalog' AND noLongerInCatalog = 0",
    )
    suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<PrintingEntity>

    /** Every row [RoomCatalogStore.search] looks through: the Catalog rows of one Language that are still listed. */
    @Query("SELECT * FROM printing WHERE game = :game AND language = :language AND origin = 'Catalog' AND noLongerInCatalog = 0")
    suspend fun searchable(game: Game, language: Language): List<PrintingEntity>
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

    @Query("UPDATE owned_copy SET condition = :condition, locationId = :locationId, notes = :notes WHERE id = :id")
    suspend fun update(id: Long, condition: Condition, locationId: Long?, notes: String)

    @Query("UPDATE owned_copy SET game = :game, language = :language, sourceId = :sourceId WHERE id = :id")
    suspend fun move(id: Long, game: Game, language: Language, sourceId: String)

    @Query("DELETE FROM owned_copy WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT game, language, sourceId, COUNT(*) AS ownedCopyCount FROM owned_copy GROUP BY game, language, sourceId")
    suspend fun countsByPrinting(): List<PrintingCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomPrintings(printings: List<PrintingEntity>)

    @Query("SELECT * FROM printing WHERE game = :game AND language = :language AND sourceId = :sourceId AND origin = 'Custom'")
    suspend fun customPrinting(game: Game, language: Language, sourceId: String): PrintingEntity?

    @Query("SELECT * FROM printing WHERE game = :game AND language = :language AND origin = 'Custom'")
    suspend fun customPrintings(game: Game, language: Language): List<PrintingEntity>

    @Query("SELECT * FROM printing WHERE origin = 'Custom'")
    suspend fun allCustomPrintings(): List<PrintingEntity>

    @Query(
        "SELECT * FROM printing WHERE origin = 'Catalog' AND noLongerInCatalog = 1 AND EXISTS " +
            "(SELECT 1 FROM owned_copy c WHERE c.game = printing.game AND c.language = printing.language AND c.sourceId = printing.sourceId)",
    )
    suspend fun flaggedCatalogPrintings(): List<PrintingEntity>

    @Query("DELETE FROM printing WHERE game = :game AND language = :language AND sourceId = :sourceId AND origin = 'Custom'")
    suspend fun deleteCustomPrinting(game: Game, language: Language, sourceId: String)

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

    @Query("DELETE FROM printing WHERE origin = 'Custom'")
    suspend fun deleteAllCustomPrintings()

    /** Keeps a Catalog row the app already has: the Catalog's details win over a backup's last-known ones. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLastKnownPrintings(printings: List<PrintingEntity>)

    /** One transaction, so the Locations, Owned Copies and Printings are read as of the same moment. */
    @Transaction
    suspend fun snapshot(): CollectionRows =
        CollectionRows(locations(), allCopies(), allCustomPrintings(), flaggedCatalogPrintings())

    /** One transaction: a failed insert rolls back the deletes too, leaving the old Collection in place. */
    @Transaction
    suspend fun replaceAll(
        locations: List<LocationEntity>,
        copies: List<OwnedCopyEntity>,
        customPrintings: List<PrintingEntity>,
        lastKnownPrintings: List<PrintingEntity>,
    ) {
        deleteAllCopies()
        deleteAllLocations()
        deleteAllCustomPrintings()
        insertLocations(locations)
        insertAll(copies)
        upsertCustomPrintings(customPrintings)
        insertLastKnownPrintings(lastKnownPrintings)
    }
}

class CollectionRows(
    val locations: List<LocationEntity>,
    val copies: List<OwnedCopyEntity>,
    val customPrintings: List<PrintingEntity>,
    val lastKnownPrintings: List<PrintingEntity>,
)

@Dao
interface BackupStatusDao {
    @Query("SELECT * FROM backup_status WHERE id = 0")
    suspend fun load(): BackupStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(status: BackupStatusEntity)
}

data class PrintingCount(val game: Game, val language: Language, val sourceId: String, val ownedCopyCount: Int)
