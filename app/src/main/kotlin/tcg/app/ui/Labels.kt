package tcg.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Variant

/** Shown where a Printing's details are the last-known ones because a Catalog refresh dropped it. */
@Composable
fun NoLongerInCatalogFlag() {
    Text(
        "No longer in the Catalog",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
    )
}

/** Shown where the Catalog has no record of a Printing, so only its identifier and Language are known. */
@Composable
fun UnresolvedPrintingFlag() {
    Text(
        "Unresolved Printing",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
    )
}

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
