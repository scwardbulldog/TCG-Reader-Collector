package tcg.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import tcg.domain.Condition
import tcg.domain.Language
import tcg.domain.Printing
import tcg.domain.Variant
import tcg.domain.collection.PrintingTitle
import tcg.domain.collection.PrintingTitles

/** Where views get Card names to title a Printing; provided once at the root. */
val LocalPrintingTitles = staticCompositionLocalOf<PrintingTitles> { error("PrintingTitles not provided") }

/**
 * The title of [printing]. It shows the Printing's own name first, then the English title and Japanese subtext
 * once the Catalog lookup returns.
 */
@Composable
fun rememberPrintingTitle(printing: Printing): PrintingTitle {
    val titles = LocalPrintingTitles.current
    return produceState(PrintingTitle.of(printing, null), printing, titles) {
        value = titles.of(printing)
    }.value
}

/** A title with its smaller subtext beneath. */
@Composable
fun PrintingHeading(title: PrintingTitle, titleStyle: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(title.title, style = titleStyle)
    title.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

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
