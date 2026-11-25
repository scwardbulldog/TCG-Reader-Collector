package tcg.domain

/** The Catalog: every Printing the app knows about. Stored apart from the Collection. */
interface CatalogStore {
    /**
     * Replaces all Printings of one Game and Language, and the [version] they came from, in one
     * transaction. Never touches the Collection.
     */
    suspend fun replaceCatalog(game: Game, language: Language, printings: List<Printing>, version: String? = null)

    /** The source version of the stored Catalog for one Game and Language; null if none was recorded. */
    suspend fun catalogVersion(game: Game, language: Language): String?

    suspend fun find(identifier: PrintingIdentifier): Printing?

    /** Base Number is a lookup aid, so several Printings (Variants) can match. */
    suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing>
}

enum class DeleteLocationResult { Deleted, NotEmpty }

/**
 * The Collection: Owned Copies and Locations. An Owned Copy may reference a
 * Printing identifier the Catalog does not contain.
 */
interface CollectionStore {
    suspend fun add(copy: NewOwnedCopy): OwnedCopy

    suspend fun ownedCopies(printing: PrintingIdentifier): List<OwnedCopy>

    /** How many Owned Copies each Printing has. */
    suspend fun countsByPrinting(): Map<PrintingIdentifier, Int>

    suspend fun createLocation(name: String): Location

    suspend fun locations(): List<Location>

    suspend fun renameLocation(id: LocationId, name: String)

    suspend fun deleteLocation(id: LocationId): DeleteLocationResult
}
