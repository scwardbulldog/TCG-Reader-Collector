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
    private const val ROMANCE_DAWN = "Romance Dawn"
    private const val PREMIUM_BOOSTER = "Premium Booster -The Best-"
    private const val PARAMOUNT_WAR = "Paramount War"

    private fun printing(
        language: Language,
        sourceId: String,
        name: String,
        rarity: String = "L",
        releaseProduct: String = ROMANCE_DAWN,
    ) = Printing(
        identifier = PrintingIdentifier(Game.OnePiece, language, sourceId),
        baseNumber = sourceId.substringBefore('_'),
        releaseProduct = releaseProduct,
        rarity = rarity,
        name = name,
        imageUrl = "",
    )

    private val sampleCatalog = mapOf(
        Language.English to listOf(
            printing(Language.English, "OP01-001", "Roronoa Zoro"),
            printing(Language.English, "OP01-001_p1", "Roronoa Zoro"),
            printing(Language.English, "OP02-013_r1", "Portgas.D.Ace", "SR", PREMIUM_BOOSTER),
            printing(Language.English, "OP01-016", "Nami", "R"),
            printing(Language.English, "OP02-013", "Portgas.D.Ace", "SR", PARAMOUNT_WAR),
        ),
        Language.Japanese to listOf(printing(Language.Japanese, "OP01-001", "ロロノア・ゾロ")),
    )

    /**
     * Does nothing unless the Collection is empty. A Language's sample Catalog is added only if it has none.
     * Spreads copies over Locations so every filter has something to match: three Zoro copies are split
     * two in Binder 1 and one in Bulk box A, and some copies have no Location.
     */
    suspend fun seed(catalog: CatalogStore, collection: CollectionStore) {
        if (collection.countsByPrinting().isNotEmpty()) return
        sampleCatalog.forEach { (language, printings) ->
            if (catalog.catalogVersion(Game.OnePiece, language) == null && printings.none { catalog.find(it.identifier) != null }) {
                catalog.replaceCatalog(Game.OnePiece, language, printings)
            }
        }
        val binder = collection.createLocation("Binder 1").id
        val bulk = collection.createLocation("Bulk box A").id
        fun id(language: Language, sourceId: String) = PrintingIdentifier(Game.OnePiece, language, sourceId)
        val en = Language.English
        val copies = listOf(
            id(en, "OP01-001") to listOf(binder, binder, bulk),
            id(en, "OP01-001_p1") to listOf(binder),
            id(en, "OP02-013_r1") to listOf(bulk, null),
            id(en, "OP01-016") to listOf(null),
            id(en, "OP02-013") to listOf(binder, null),
            id(Language.Japanese, "OP01-001") to listOf(bulk, null),
            // A Printing the Catalog doesn't know, to see an Unresolved Printing row.
            id(en, "ST99-007_p2") to listOf(binder),
        )
        for ((printing, locations) in copies) {
            locations.forEachIndexed { index, location ->
                collection.add(
                    NewOwnedCopy(
                        printing,
                        condition = if (index == 0) Condition.NearMint else Condition.LightlyPlayed,
                        location = location,
                        notes = if (index == 1) "corner wear" else "",
                    ),
                )
            }
        }
    }
}
