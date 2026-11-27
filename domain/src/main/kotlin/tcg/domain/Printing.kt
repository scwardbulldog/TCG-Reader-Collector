package tcg.domain

/** Identity of a Printing: Game + Language + the source's own card-list ID, treated as opaque. */
data class PrintingIdentifier(val game: Game, val language: Language, val sourceId: String)

/**
 * A Printing row. Everything except [identifier] is a detail, never identity.
 * [variant] is null for a Custom Printing, and for a flagged row migrated from v1 until a refresh lists it again.
 * [productCode] is for sorting and grouping only. [treatments] null means unknown, not plain.
 * [noLongerInCatalog] marks a Catalog row a refresh dropped while an Owned Copy still uses it; its details are then the last-known ones.
 */
data class Printing(
    val identifier: PrintingIdentifier,
    val baseNumber: String,
    val releaseProduct: String,
    val rarity: String,
    val name: String,
    val imageUrl: String,
    val variant: Variant?,
    val productCode: String? = null,
    val treatments: List<String>? = null,
    val noLongerInCatalog: Boolean = false,
)

/** Custom Printing ids live in this namespace, which no Bandai ID (or ID the Catalog pipeline derives) can enter. */
const val CUSTOM_ID_PREFIX = "custom:"

/** True for a Custom Printing: one the collector recorded because the Catalog has none. */
val PrintingIdentifier.isCustom: Boolean get() = sourceId.startsWith(CUSTOM_ID_PREFIX)

enum class Variant { Normal, AlternateArt, Reprint }
