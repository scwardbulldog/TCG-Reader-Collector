package tcg.domain.onepiece

import tcg.domain.Variant

/** One Piece-specific rules. Other Games do not inherit Bandai's suffix meanings. */
object OnePiece {
    private val alternateArtSuffix = Regex("_p\\d+$")
    private val reprintSuffix = Regex("_r\\d+$")

    fun variantOf(sourceId: String): Variant = when {
        alternateArtSuffix.containsMatchIn(sourceId) -> Variant.AlternateArt
        reprintSuffix.containsMatchIn(sourceId) -> Variant.Reprint
        else -> Variant.Normal
    }
}
