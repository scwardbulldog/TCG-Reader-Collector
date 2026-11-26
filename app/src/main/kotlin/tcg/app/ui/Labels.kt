package tcg.app.ui

import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Variant

fun Language.label() = when (this) {
    Language.English -> "EN"
    Language.Japanese -> "JP"
}

fun Condition.label() = when (this) {
    Condition.NearMint -> "Near Mint"
    Condition.LightlyPlayed -> "Lightly Played"
    Condition.ModeratelyPlayed -> "Moderately Played"
    Condition.HeavilyPlayed -> "Heavily Played"
    Condition.Damaged -> "Damaged"
}

fun Variant.label() = when (this) {
    Variant.Normal -> "Normal"
    Variant.AlternateArt -> "Alternate Art"
    Variant.Reprint -> "Reprint"
}
