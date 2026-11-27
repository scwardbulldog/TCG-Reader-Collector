package tcg.domain

/** Identity of a Printing: Game + Language + the source's own card-list ID, treated as opaque. */
data class PrintingIdentifier(val game: Game, val language: Language, val sourceId: String)

/**
 * A Catalog entry. Everything except [identifier] is a detail, never identity.
 * [variant] is null only for a Custom Printing. [productCode] is for sorting and grouping only.
 * [treatments] null means unknown, not plain.
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
)

/** Custom Printing ids live in this namespace, which no Bandai ID (or ID the Catalog pipeline derives) can enter. */
const val CUSTOM_ID_PREFIX = "custom:"

/** True for a Custom Printing: one the collector recorded because the Catalog has none. */
val PrintingIdentifier.isCustom: Boolean get() = sourceId.startsWith(CUSTOM_ID_PREFIX)

enum class Variant { Normal, AlternateArt, Reprint }
