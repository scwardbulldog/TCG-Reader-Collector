package tcg.domain

/** Identity of a Printing: Game + Language + the source's own card-list ID, treated as opaque. */
data class PrintingIdentifier(val game: Game, val language: Language, val sourceId: String)

/** A Catalog entry. Everything except [identifier] is a detail, never identity. */
data class Printing(
    val identifier: PrintingIdentifier,
    val baseNumber: String,
    val releaseProduct: String,
    val rarity: String,
    val name: String,
    val imageUrl: String,
)

enum class Variant { Normal, AlternateArt, Reprint }
