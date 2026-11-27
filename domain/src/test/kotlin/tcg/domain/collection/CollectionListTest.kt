package tcg.domain.collection

import kotlinx.coroutines.test.runTest
import tcg.domain.Game
import tcg.domain.Language
import tcg.domain.NewOwnedCopy
import tcg.domain.Printing
import tcg.domain.PrintingIdentifier
import tcg.domain.Variant
import tcg.domain.catalog.InMemoryCatalogStore
import tcg.domain.fixtureVariant
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionListTest {
    private val catalog = InMemoryCatalogStore()
    private val collection = InMemoryCollectionStore()
    private val list = CollectionList(collection, catalog)
    private val catalogued = mutableMapOf<Language, List<Printing>>()

    private fun id(language: Language, sourceId: String) = PrintingIdentifier(Game.OnePiece, language, sourceId)

    /** Adds a Printing to the Catalog and gives the collector [copies] Owned Copies of it. */
    private suspend fun own(
        language: Language,
        sourceId: String,
        baseNumber: String,
        name: String,
        copies: Int = 1,
        releaseProduct: String = "Romance Dawn",
        productCode: String? = null,
    ): PrintingIdentifier {
        val identifier = id(language, sourceId)
        catalogued[language] = catalogued[language].orEmpty() +
            Printing(identifier, baseNumber, releaseProduct, "L", name, "https://example.invalid/$sourceId.png", fixtureVariant(sourceId), productCode)
        catalog.replaceCatalog(Game.OnePiece, language, catalogued.getValue(language))
        repeat(copies) { collection.add(NewOwnedCopy(identifier)) }
        return identifier
    }

    /** Owned Copies of a Printing the Catalog has no record of. */
    private suspend fun ownUnresolved(language: Language, sourceId: String, copies: Int = 1): PrintingIdentifier {
        val identifier = id(language, sourceId)
        repeat(copies) { collection.add(NewOwnedCopy(identifier)) }
        return identifier
    }

    @Test
    fun `an empty Collection has no rows`() = runTest {
        assertEquals(emptyList(), list.rows())
    }

    @Test
    fun `each Printing is one row with its derived Owned Copy count`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", copies = 3)
        val nami = own(Language.English, "OP01-016", "OP01-016", "Nami")

        assertEquals(listOf(zoro to 3, nami to 1), list.rows().map { it.printing to it.ownedCopyCount })
    }

    @Test
    fun `the same Base Number in two Languages or Variants stays separate rows`() = runTest {
        val en = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val jp = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ", copies = 2)
        val alt = own(Language.English, "OP01-001_p1", "OP01-001", "Roronoa Zoro")
        val reprint = own(Language.English, "OP01-001_r1", "OP01-001", "Roronoa Zoro")

        assertEquals(setOf(en, jp, alt, reprint), list.rows().map { it.printing }.toSet())
        assertEquals(4, list.rows().size)
    }

    @Test
    fun `search is case-insensitive and partial on Base Number or name`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val nami = own(Language.English, "OP01-016", "OP01-016", "Nami")

        assertEquals(listOf(zoro), list.rows("zoro").map { it.printing })
        assertEquals(listOf(zoro), list.rows("RONOA").map { it.printing })
        assertEquals(listOf(nami), list.rows("op01-01").map { it.printing })
        assertEquals(listOf(zoro, nami), list.rows("  ").map { it.printing })
        assertEquals(emptyList(), list.rows("luffy").map { it.printing })
    }

    @Test
    fun `a name in either Language finds the owned Printings of the Card, still one row per Printing, and the Language filter narrows`() = runTest {
        val en = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val jp = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ", copies = 2)
        val jpAlt = own(Language.Japanese, "OP01-001_p1", "OP01-001", "ロロノア・ゾロ")
        val nami = own(Language.English, "OP01-016", "OP01-016", "Nami")

        assertEquals(setOf(en, jp, jpAlt), list.rows("zoro").map { it.printing }.toSet())
        assertEquals(setOf(en, jp, jpAlt), list.rows("ゾロ").map { it.printing }.toSet())
        assertEquals(listOf(jp, jpAlt), list.rows("zoro", CollectionFilter(language = Language.Japanese)).map { it.printing })
        assertEquals(listOf(nami), list.rows("nami").map { it.printing })
    }

    @Test
    fun `an Unresolved Printing matches search on its Printing identifier`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val unresolved = ownUnresolved(Language.English, "ST99-007_p2")

        assertEquals(listOf(unresolved), list.rows("st99-007").map { it.printing })
        assertEquals(listOf(unresolved), list.rows("_P2").map { it.printing })
    }

    @Test
    fun `the Language filter matches the Printing's Language`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val jp = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")

        assertEquals(listOf(jp), list.rows(filter = CollectionFilter(language = Language.Japanese)).map { it.printing })
    }

    @Test
    fun `a Reprint appears under its own Release Product, not its Base Number's`() = runTest {
        val original = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", releaseProduct = "Romance Dawn")
        val reprint = own(Language.English, "OP01-001_r1", "OP01-001", "Roronoa Zoro", releaseProduct = "Premium Booster")

        assertEquals(listOf(reprint), list.rows(filter = CollectionFilter(releaseProduct = "Premium Booster")).map { it.printing })
        assertEquals(listOf(original), list.rows(filter = CollectionFilter(releaseProduct = "Romance Dawn")).map { it.printing })
    }

    @Test
    fun `the Variant filter matches the published Variant`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val alt = own(Language.English, "OP01-001_p1", "OP01-001", "Roronoa Zoro")
        val reprint = own(Language.English, "OP01-001_r1", "OP01-001", "Roronoa Zoro")

        assertEquals(listOf(alt), list.rows(filter = CollectionFilter(variant = Variant.AlternateArt)).map { it.printing })
        assertEquals(listOf(reprint), list.rows(filter = CollectionFilter(variant = Variant.Reprint)).map { it.printing })
    }

    @Test
    fun `an Unresolved Printing is excluded by Release Product and Variant filters but matches Language`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val unresolved = ownUnresolved(Language.English, "ST99-007")

        assertEquals(listOf(zoro), list.rows(filter = CollectionFilter(releaseProduct = "Romance Dawn")).map { it.printing })
        assertEquals(listOf(zoro), list.rows(filter = CollectionFilter(variant = Variant.Normal)).map { it.printing })
        assertEquals(listOf(zoro, unresolved), list.rows(filter = CollectionFilter(language = Language.English)).map { it.printing })
    }

    @Test
    fun `the Location filter counts only the Owned Copies kept there`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", copies = 3)
        val nami = own(Language.English, "OP01-016", "OP01-016", "Nami")
        val binder = collection.createLocation("Binder 1")
        val bulk = collection.createLocation("Bulk box A")
        val (first, second, third) = collection.ownedCopies(zoro)
        collection.update(first.copy(location = binder.id))
        collection.update(second.copy(location = binder.id))
        collection.update(third.copy(location = bulk.id))

        assertEquals(listOf(zoro to 2), list.rows(filter = CollectionFilter(location = binder.id)).map { it.printing to it.ownedCopyCount })
        assertEquals(listOf(zoro to 1), list.rows(filter = CollectionFilter(location = bulk.id)).map { it.printing to it.ownedCopyCount })
        assertEquals(listOf(zoro to 3, nami to 1), list.rows().map { it.printing to it.ownedCopyCount })
    }

    @Test
    fun `an Unresolved Printing matches the Location filter`() = runTest {
        val binder = collection.createLocation("Binder 1")
        val unresolved = ownUnresolved(Language.English, "ST99-007")
        collection.update(collection.ownedCopies(unresolved).single().copy(location = binder.id))

        assertEquals(listOf(unresolved), list.rows(filter = CollectionFilter(location = binder.id)).map { it.printing })
    }

    @Test
    fun `filters and search combine with AND`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val jpZoro = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
        own(Language.Japanese, "OP01-016", "OP01-016", "ナミ")
        val filter = CollectionFilter(language = Language.Japanese, releaseProduct = "Romance Dawn", variant = Variant.Normal)

        assertEquals(listOf(jpZoro), list.rows("OP01-001", filter).map { it.printing })
        assertEquals(emptyList(), list.rows("luffy", filter).map { it.printing })
    }

    @Test
    fun `filter options list the owned Release Products`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", releaseProduct = "Romance Dawn")
        own(Language.English, "OP01-001_r1", "OP01-001", "Roronoa Zoro", releaseProduct = "Premium Booster")
        own(Language.Japanese, "OP01-016", "OP01-016", "ナミ", releaseProduct = "Premium Booster")
        ownUnresolved(Language.English, "ST99-007")

        assertEquals(listOf("Premium Booster", "Romance Dawn"), list.releaseProducts().map { it.releaseProduct })
    }

    @Test
    fun `rows sort by name, newest added, or Product code, falling back to Base Number`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", productCode = "OP-02")
        val nami = own(Language.English, "OP01-016", "OP01-016", "nami", productCode = "OP-01")
        val ace = own(Language.English, "OP02-013", "OP02-013", "Ace")
        collection.add(NewOwnedCopy(zoro))

        assertEquals(listOf(ace, nami, zoro), list.rows(sort = CollectionSort.Name).map { it.printing })
        assertEquals(listOf(zoro, ace, nami), list.rows(sort = CollectionSort.DateAdded).map { it.printing })
        assertEquals(listOf(nami, zoro, ace), list.rows(sort = CollectionSort.ProductCode).map { it.printing })
        assertEquals(listOf(zoro, nami, ace), list.rows().map { it.printing })
    }

    @Test
    fun `filter options show the Product code first and are ordered by it`() = runTest {
        own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", releaseProduct = "Romance Dawn", productCode = "OP-01")
        own(Language.English, "OP02-013", "OP02-013", "Ace", releaseProduct = "Paramount War", productCode = "OP-02")
        own(Language.English, "P-001", "P-001", "Promo", releaseProduct = "Event Pack")

        assertEquals(
            listOf("OP-01 · Romance Dawn", "OP-02 · Paramount War", "Event Pack"),
            list.releaseProducts().map { it.label },
        )
        assertEquals(listOf("Paramount War"), list.releaseProducts().filter { it.productCode == "OP-02" }.map { it.releaseProduct })
    }

    @Test
    fun `deleting a Location in use gives its Owned Copies Location None and keeps them`() = runTest {
        val zoro = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro", copies = 2)
        val binder = collection.createLocation("Binder 1")
        collection.update(collection.ownedCopies(zoro).first().copy(location = binder.id))

        assertEquals(listOf(LocationUse(binder, 1)), list.locationUses())
        list.deleteLocation(binder.id)

        assertEquals(emptyList(), list.locationUses())
        assertEquals(listOf(null, null), collection.ownedCopies(zoro).map { it.location })
    }

    @Test
    fun `a Location is renamed with surrounding space trimmed, and never to nothing`() = runTest {
        val binder = collection.createLocation("Binder 1")
        list.renameLocation(binder.id, "  Binder One ")
        list.renameLocation(binder.id, "   ")

        assertEquals(listOf("Binder One"), list.locations().map { it.name })
    }

    @Test
    fun `rows are ordered by Base Number then Language then Printing identifier with no Base Number last`() = runTest {
        val unresolvedEn = ownUnresolved(Language.English, "AA00-001")
        val op02 = own(Language.English, "OP02-013", "OP02-013", "Portgas.D.Ace")
        val op01Jp = own(Language.Japanese, "OP01-001", "OP01-001", "ロロノア・ゾロ")
        val op01EnAlt = own(Language.English, "OP01-001_p1", "OP01-001", "Roronoa Zoro")
        val op01En = own(Language.English, "OP01-001", "OP01-001", "Roronoa Zoro")
        val unresolvedJp = ownUnresolved(Language.Japanese, "AA00-001")

        assertEquals(
            listOf(op01En, op01EnAlt, op01Jp, op02, unresolvedEn, unresolvedJp),
            list.rows().map { it.printing },
        )
    }
}
