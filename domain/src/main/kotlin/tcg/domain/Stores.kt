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

    /** Printings of one Game and Language whose Base Number or name contains [query], ignoring case. */
    suspend fun search(game: Game, language: Language, query: String): List<Printing>
}

enum class DeleteLocationResult { Deleted, NotEmpty }

/**
 * The Collection: Owned Copies, Locations and Custom Printings. An Owned Copy may reference a
 * Printing identifier the Catalog does not contain.
 */
interface CollectionStore {
    suspend fun add(copy: NewOwnedCopy): OwnedCopy

    /** Adds every copy or none: a failure part-way leaves the Collection unchanged. */
    suspend fun addAll(copies: List<NewOwnedCopy>): List<OwnedCopy>
    suspend fun ownedCopies(printing: PrintingIdentifier): List<OwnedCopy>

    /** Saves a changed condition, Location or notes; the Printing of an Owned Copy never changes. */
    suspend fun update(copy: OwnedCopy)

    suspend fun delete(id: OwnedCopyId)

    /** How many Owned Copies each Printing has. */
    suspend fun countsByPrinting(): Map<PrintingIdentifier, Int>

    /** Saves the details of Printings Owned Copies refer to, replacing earlier ones, so they outlive a Catalog refresh. */
    suspend fun rememberPrintings(printings: List<Printing>)

    /** The details last saved by [rememberPrintings]; null if none were. */
    suspend fun lastKnownPrinting(identifier: PrintingIdentifier): Printing?

    /**
     * The Custom Printings of one Game and Language. They are saved with [rememberPrintings], which keeps them
     * apart from the Catalog, and a Catalog refresh never touches them.
     */
    suspend fun customPrintings(game: Game, language: Language): List<Printing>

    /** Removes a Custom Printing's saved details. Callers make sure no Owned Copy still uses it. */
    suspend fun deleteCustomPrinting(identifier: PrintingIdentifier)

    suspend fun createLocation(name: String): Location

    suspend fun locations(): List<Location>

    suspend fun renameLocation(id: LocationId, name: String)

    suspend fun deleteLocation(id: LocationId): DeleteLocationResult

    /** Every Location and Owned Copy, read as one consistent snapshot. */
    suspend fun snapshot(): CollectionSnapshot

    /**
     * Replaces the whole Collection (Owned Copies, Locations and Custom Printings) with [snapshot],
     * keeping its ids and dropping saved last-known Printings. Atomic: a failure part-way leaves the existing Collection unchanged.
     */
    suspend fun replaceCollection(snapshot: CollectionSnapshot)
}
