package tcg.domain.collection

import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.LocationId
import tcg.domain.OwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant

/** The Collection as CSV text for Google Sheets: one row per Owned Copy, no quantity column. */
object CollectionCsv {
    private val columns = listOf(
        "Game", "Language", "Base Number", "Printing identifier", "Name", "English name", "Variant",
        "Release Product", "Condition", "Location", "Notes",
    )

    /**
     * [details], [locations] and [englishNames] (the Card's English name per Printing) may lack entries; the
     * matching cells are then blank. `Name` is the Printing's own name.
     */
    fun build(
        copies: List<OwnedCopy>,
        details: Map<PrintingIdentifier, Printing>,
        locations: Map<LocationId, String>,
        englishNames: Map<PrintingIdentifier, String> = emptyMap(),
    ): String = (listOf(columns) + copies.map { row(it, details[it.printing], locations, englishNames[it.printing]) })
        .joinToString("") { cells -> cells.joinToString(",", transform = ::escape) + "\r\n" }

    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    private fun row(copy: OwnedCopy, printing: Printing?, locations: Map<LocationId, String>, englishName: String?): List<String> {
        val id = copy.printing
        return listOf(
            id.game.code(),
            id.language.code(),
            printing?.baseNumber.orEmpty(),
            "${id.game.code()} / ${id.language.code()} / ${id.sourceId}",
            printing?.name.orEmpty(),
            englishName.orEmpty(),
            printing?.variant?.label().orEmpty(),
            printing?.releaseProduct.orEmpty(),
            copy.condition.label(),
            copy.location?.let { locations[it] }.orEmpty(),
            copy.notes,
        )
    }

    // The labels below are the export's own contract, so UI wording changes must not alter exported cells.
    private fun Game.code() = name.lowercase()

    private fun Language.code() = when (this) {
        Language.English -> "EN"
        Language.Japanese -> "JP"
    }

    private fun Variant.label() = when (this) {
        Variant.Normal -> "Normal"
        Variant.AlternateArt -> "Alternate Art"
        Variant.Reprint -> "Reprint"
    }

    private fun Condition.label() = when (this) {
        Condition.NearMint -> "Near Mint"
        Condition.LightlyPlayed -> "Lightly Played"
        Condition.ModeratelyPlayed -> "Moderately Played"
        Condition.HeavilyPlayed -> "Heavily Played"
        Condition.Damaged -> "Damaged"
    }
}
