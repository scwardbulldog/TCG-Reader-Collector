package tcg.domain

private val alternateArtSuffix = Regex("_p\\d+$")
private val reprintSuffix = Regex("_r\\d+$")

/** Stands in for the Catalog pipeline in test fixtures: the Variant Bandai's ID suffix suggests. Production code must not do this. */
fun fixtureVariant(sourceId: String): Variant = when {
    alternateArtSuffix.containsMatchIn(sourceId) -> Variant.AlternateArt
    reprintSuffix.containsMatchIn(sourceId) -> Variant.Reprint
    else -> Variant.Normal
}
