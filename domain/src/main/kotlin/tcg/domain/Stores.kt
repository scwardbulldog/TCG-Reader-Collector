package tcg.domain

/**
 * The Catalog: every Printing the app knows about. Stored apart from the Collection in effect (a refresh never
 * changes an Owned Copy), though in one Printing table with the Custom Printings.
 */
interface CatalogStore {
    /**
     * Refreshes all Catalog Printings of one Game and Language, and the [version] they came from, in one
     * transaction, by the [tcg.domain.catalog.CatalogRefresh] rules: a dropped Printing an Owned Copy uses stays
     * flagged, an unused one goes. Never touches an Owned Copy or a Custom Printing.
     */
    suspend fun replaceCatalog(game: Game, language: Language, printings: List<Printing>, version: String? = null)

    /** The source version of the stored Catalog for one Game and Language; null if none was recorded. */
    suspend fun catalogVersion(game: Game, language: Language): String?

    /** A Catalog row, including one flagged as no longer in the Catalog; never a Custom Printing. */
    suspend fun find(identifier: PrintingIdentifier): Printing?

    /** Base Number is a lookup aid, so several Printings (Variants) can match. Flagged rows are not offered. */
    suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing>

    /** Printings of one Game and Language whose Base Number or name contains [query], ignoring case. Flagged rows are not offered. */
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

    /** Saves a Custom Printing, replacing an earlier one with the same identifier. A Catalog refresh never touches it. */
    suspend fun saveCustomPrinting(printing: Printing)

    /** The Custom Printing with [identifier]; null if there is none. */
    suspend fun customPrinting(identifier: PrintingIdentifier): Printing?

    /** The Custom Printings of one Game and Language. */
    suspend fun customPrintings(game: Game, language: Language): List<Printing>

    /** Removes a Custom Printing. Callers make sure no Owned Copy still uses it. */
    suspend fun deleteCustomPrinting(identifier: PrintingIdentifier)

    suspend fun createLocation(name: String): Location

    suspend fun locations(): List<Location>

    suspend fun renameLocation(id: LocationId, name: String)

    suspend fun deleteLocation(id: LocationId): DeleteLocationResult

    /** Every Location and Owned Copy, read as one consistent snapshot. */
    suspend fun snapshot(): CollectionSnapshot

    /**
     * Replaces the whole Collection (Owned Copies, Locations and Custom Printings) with [snapshot],
     * keeping its ids and leaving Catalog rows as they are. Atomic: a failure part-way leaves the existing Collection unchanged.
     */
    suspend fun replaceCollection(snapshot: CollectionSnapshot)
}
