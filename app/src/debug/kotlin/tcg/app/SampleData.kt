package tcg.app

import tcg.domain.CatalogStore
import tcg.domain.CollectionStore
import tcg.domain.Condition
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier

/** Debug-only sample Collection, for trying the UI before scanning and the Catalog download are usable. */
object SampleData {
    private fun printing(language: Language, sourceId: String, name: String, rarity: String = "L") =
        Printing(
            identifier = PrintingIdentifier(Game.OnePiece, language, sourceId),
            baseNumber = sourceId.substringBefore('_'),
            releaseProduct = "Romance Dawn",
            rarity = rarity,
            name = name,
            imageUrl = "",
        )

    private val sampleCatalog = mapOf(
        Language.English to listOf(
            printing(Language.English, "OP01-001", "Roronoa Zoro"),
            printing(Language.English, "OP01-001_p1", "Roronoa Zoro"),
            printing(Language.English, "OP01-001_r1", "Roronoa Zoro"),
            printing(Language.English, "OP01-016", "Nami", "R"),
            printing(Language.English, "OP02-013", "Portgas.D.Ace", "SR"),
        ),
        Language.Japanese to listOf(printing(Language.Japanese, "OP01-001", "ロロノア・ゾロ")),
    )

    /** Does nothing unless the Collection is empty. A Language's sample Catalog is added only if it has none. */
    suspend fun seed(catalog: CatalogStore, collection: CollectionStore) {
        if (collection.countsByPrinting().isNotEmpty()) return
        sampleCatalog.forEach { (language, printings) ->
            if (catalog.catalogVersion(Game.OnePiece, language) == null && printings.none { catalog.find(it.identifier) != null }) {
                catalog.replaceCatalog(Game.OnePiece, language, printings)
            }
        }
        val binder = collection.createLocation("Binder 3")
        val copies = listOf(
            "OP01-001" to 3, "OP01-001_p1" to 1, "OP01-001_r1" to 2, "OP01-016" to 1, "OP02-013" to 2,
        )
        for ((sourceId, count) in copies) {
            repeat(count) { index ->
                collection.add(
                    NewOwnedCopy(
                        PrintingIdentifier(Game.OnePiece, Language.English, sourceId),
                        condition = if (index == 0) Condition.NearMint else Condition.LightlyPlayed,
                        location = binder.id.takeIf { index == 0 },
                        notes = if (index == 1) "corner wear" else "",
                    ),
                )
            }
        }
        repeat(2) { collection.add(NewOwnedCopy(PrintingIdentifier(Game.OnePiece, Language.Japanese, "OP01-001"))) }
        // A Printing the Catalog doesn't know, to see an Unresolved Printing row.
        collection.add(NewOwnedCopy(PrintingIdentifier(Game.OnePiece, Language.English, "ST99-007_p2")))
    }
}
