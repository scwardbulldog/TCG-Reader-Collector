package tcg.domain

import java.time.Instant

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
    suspend fun replaceCatalog(
        game: Game,
        language: Language,
        printings: List<Printing>,
        version: String? = null,
        generatedAt: Instant? = null,
    )

    /** The source version of the stored Catalog for one Game and Language; null if none was recorded. */
    suspend fun catalogVersion(game: Game, language: Language): String?

    /** When the oldest stored Catalog file was generated, so the date never overstates how fresh the Catalog is; null if none was recorded. */
    suspend fun catalogGeneratedAt(): Instant?

    /** A Catalog row, including one flagged as no longer in the Catalog; never a Custom Printing. */
    suspend fun find(identifier: PrintingIdentifier): Printing?

    /** Base Number is a lookup aid, so several Printings (Variants) can match. Flagged rows are not offered. */
    suspend fun findByBaseNumber(game: Game, language: Language, baseNumber: String): List<Printing>

    /**
     * Printings of one Game and Language whose Base Number or name contains [query], ignoring case and
     * full-width/half-width forms (see [containsFolded]). Flagged rows are not offered.
     */
    suspend fun search(game: Game, language: Language, query: String): List<Printing>
}

/**
 * Where the Card photos of Custom Printings are kept: one processed picture per Custom Printing, in storage the
 * collector's data lives in. The Collection holds only the opaque reference [save] returns.
 */
interface CardPhotoStore {
    /**
     * Keeps the picture the capture produced at [captured] (a location only the implementation understands) as
     * the Card photo of [printing], replacing any earlier one, and returns the reference to keep on the Printing.
     * The reference changes on every save. Throws if the picture cannot be read or kept; the earlier photo then stays.
     */
    suspend fun save(printing: PrintingIdentifier, captured: String): String

    /** Where a view can load the photo [reference] points to from; null if it is gone. */
    fun resolve(reference: String): String?

    /** Deletes the photo of [printing], if it has one. */
    suspend fun delete(printing: PrintingIdentifier)

    /** Deletes every photo. */
    suspend fun deleteAll()

    /** The JPEG bytes of the photo of [printing]; null if it has none. */
    suspend fun read(printing: PrintingIdentifier): ByteArray?

    /**
     * Prepares a whole new set of photos without touching the current ones: [photos] are the new JPEGs, and the
     * current photos of the Printings in [keep] stay. Nothing changes until [StagedPhotos.commit]; after it the
     * store holds exactly the staged and kept photos. Throws if the photos cannot be staged, changing nothing.
     */
    suspend fun stage(photos: Map<PrintingIdentifier, ByteArray>, keep: Set<PrintingIdentifier>): StagedPhotos
}

/** A new set of Card photos waiting to replace the current ones, as the [CardPhotoStore] understands it. */
interface StagedPhotos {
    /** The references the Printings will have once [commit]ted: the staged photos and the kept ones that exist. */
    val references: Map<PrintingIdentifier, String>

    /** Makes the staged photos the only photos (plus the kept ones). */
    suspend fun commit()

    /** Throws the staged photos away. Safe to call after [commit] or more than once. */
    suspend fun discard()
}

/**
 * The Collection: Owned Copies, Locations and Custom Printings. An Owned Copy may reference a
 * Printing identifier the Catalog does not contain.
 */
interface CollectionStore {
    suspend fun add(copy: NewOwnedCopy): OwnedCopy

    /** Adds every copy or none: a failure part-way leaves the Collection unchanged. */
    suspend fun addAll(copies: List<NewOwnedCopy>): List<OwnedCopy>
    suspend fun ownedCopies(printing: PrintingIdentifier): List<OwnedCopy>

    /** Saves a changed condition, Location or notes. The Printing is left alone: [move] changes that. */
    suspend fun update(copy: OwnedCopy)

    /** Moves one Owned Copy to [printing], which may be any Printing; its condition, Location and notes stay. */
    suspend fun move(id: OwnedCopyId, printing: PrintingIdentifier)

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

    /** Deletes the Location; the Owned Copies in it keep everything else and get Location None. */
    suspend fun deleteLocation(id: LocationId)

    /** How many Owned Copies are in the Location. */
    suspend fun copiesIn(id: LocationId): Int

    /** Every Location and Owned Copy, read as one consistent snapshot. */
    suspend fun snapshot(): CollectionSnapshot

    /**
     * Replaces the whole Collection (Owned Copies, Locations and Custom Printings) with [snapshot],
     * keeping its ids and leaving Catalog rows as they are. Atomic: a failure part-way leaves the existing Collection unchanged.
     */
    suspend fun replaceCollection(snapshot: CollectionSnapshot)
}
